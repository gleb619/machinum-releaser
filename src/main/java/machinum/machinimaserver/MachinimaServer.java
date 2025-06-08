package machinum.machinimaserver;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Main server class
@Slf4j
public class MachinimaServer {
    
    @SneakyThrows
    public static void startAndWait(Consumer<ServerBuilder> configurator) {
        var serverRunning = new AtomicBoolean(true);
        var future = start(configurator);
        var httpServer = future.get();

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered");
            gracefulShutdown(httpServer, serverRunning);
        }));

        // Keep main thread alive and run periodic tasks
        runMainLoop(serverRunning);
    }

    private static void runMainLoop(AtomicBoolean serverRunning) {
        try {
            while (serverRunning.get()) {
                // Main application loop - perform periodic maintenance

                // Log server status every 5 minutes
                if (System.currentTimeMillis() % 300000 < 1000) {
                    log.info("Server Is Alive");
                }

                Thread.sleep(5000); // Sleep for 5 seconds
            }
        } catch (InterruptedException e) {
            log.info("Main loop interrupted");
            Thread.currentThread().interrupt();
        }
    }

    private static void gracefulShutdown(HttpServer server, AtomicBoolean serverRunning) {
        log.info("Initiating graceful shutdown...");
        serverRunning.set(false);

        try {
            // Stop accepting new requests
            if (server != null) {
                server.stop(5); // Wait up to 5 seconds for ongoing requests
                log.info("HTTP server stopped");
            }

            log.info("Graceful shutdown completed");
        } catch (Exception e) {
            log.error("Error during shutdown", e);
        }

        System.exit(0);
    }

    public static CompletableFuture<HttpServer> start(Consumer<ServerBuilder> configurator) {
        return CompletableFuture.supplyAsync(() -> {
            ServerBuilder builder = new ServerBuilder();
            configurator.accept(builder);
            int port = builder.config.getPort();
            HttpServer server = builder.build();
            server.start();
            log.info("Server started on port {}", port);
            return server;
        });
    }

    // Core context object
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Context {

        public static final String CONTENT_TYPE = "Content-Type";
        public static final String CONTENT_TYPE_TEXT = "text/plain";
        public static final String CONTENT_TYPE_JSON = "application/json";

        private HttpExchange exchange;
        private Map<String, String> pathVariables;
        private Registry registry;
        private final AtomicReference<Object> result = new AtomicReference<>(null);
        private final AtomicReference<Integer> responseStatus = new AtomicReference<>(-1);

        public String url() {
            return exchange.getRequestURI().toString();
        }

        public String path(String name) {
            return pathVariables.getOrDefault(name, "");
        }

        public String queryParam(String name) {
            String query = exchange.getRequestURI().getQuery();
            if (query == null) return "";

            return Arrays.stream(query.split("&"))
                    .filter(param -> param.startsWith(name + "="))
                    .map(param -> URLDecoder.decode(param.substring(name.length() + 1), StandardCharsets.UTF_8))
                    .findFirst()
                    .orElse("");
        }

        public String header(String name) {
            return exchange.getRequestHeaders().getFirst(name);
        }

        public String body() {
            try (InputStream is = exchange.getRequestBody()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        }

        public Integer status() {
            return responseStatus.get();
        }

        public Context render(Object data) {
            result.set(data);
            return this;
        }

        public Context drain() {
            if(!exchange.getResponseHeaders().containsKey(CONTENT_TYPE)) {
                header(CONTENT_TYPE, CONTENT_TYPE_JSON);
            }

            if(responseStatus.get().equals(-1)) {
                responseStatus.getAndSet(404);
            }

            return doSend("");
        }
        
        public Context send(Context context) {
            Object data = context.getResult().get();

            try {
                if (data instanceof String s) {
                    return header(CONTENT_TYPE, CONTENT_TYPE_TEXT).doSend(s);
                } else if(Objects.nonNull(data)){
                    ObjectMapper mapper = registry.get(ObjectMapper.class);
                    String json = mapper.writeValueAsString(data);
                    return header(CONTENT_TYPE, CONTENT_TYPE_JSON).doSend(json);
                } else {
                    return doSend("");
                }
            } catch (Exception e) {
                return status(500).doSend("{ \"error\": \"Internal server error\" }");
            }
        }

        private Context doSend(String data) {
            try {
                byte[] response = Objects.nonNull(data) ? data.getBytes(StandardCharsets.UTF_8) : new byte[0];
                if(responseStatus.get().equals(-1)) {
                    responseStatus.getAndSet(200);
                }
                exchange.sendResponseHeaders(responseStatus.get(), response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            } catch (Exception e) {
                if(log.isDebugEnabled()) {
                    log.error("Send error: [%s]".formatted(url()), e);
                } else {
                    log.error("Send error: [{}] {}", url(), e.getMessage());
                }
            }
            return this;
        }

        public Context status(int code) {
            try {
                responseStatus.set(code);
                //exchange.sendResponseHeaders(code, -1);
            } catch (Exception e) {
                if(log.isDebugEnabled()) {
                    log.error("Send error: [%s]".formatted(url()), e);
                } else {
                    log.error("Send error: [{}] {}", url(), e.getMessage());
                }
            }
            return this;
        }

        public Context header(String name, String value) {
            exchange.getResponseHeaders().add(name, value);
            return this;
        }

    }

    @Slf4j
    public static class CorsHandler {

        private final CorsConfig config;

        public CorsHandler(CorsConfig config) {
            this.config = config != null ? config : CorsConfig.builder().build();
        }

        public void handleCors(Context ctx) {
            if (!config.isEnabled()) {
                return;
            }

            String origin = ctx.header("Origin");
            String requestMethod = ctx.getExchange().getRequestMethod();

            // Handle preflight OPTIONS request
            if ("OPTIONS".equals(requestMethod)) {
                handlePreflightRequest(ctx, origin);
                return;
            }

            // Handle actual request
            handleActualRequest(ctx, origin);
        }

        private void handlePreflightRequest(Context ctx, String origin) {
            log.debug("Handling CORS preflight request from origin: {}", origin);

            // Check if origin is allowed
            if (!isOriginAllowed(origin)) {
                log.warn("CORS preflight rejected for origin: {}", origin);
                ctx.status(403);
                return;
            }

            // Get requested method and headers
            String requestedMethod = ctx.header("Access-Control-Request-Method");
            String requestedHeaders = ctx.header("Access-Control-Request-Headers");

            // Validate requested method
            if (requestedMethod != null && !config.getAllowedMethods().contains(requestedMethod.toUpperCase())) {
                log.warn("CORS preflight rejected for method: {}", requestedMethod);
                ctx.status(405);
                return;
            }

            // Validate requested headers
            if (requestedHeaders != null && !areHeadersAllowed(requestedHeaders)) {
                log.warn("CORS preflight rejected for headers: {}", requestedHeaders);
                ctx.status(400);
                return;
            }

            // Set CORS headers for preflight
            setCorsHeaders(ctx, origin, true);

            // Send successful preflight response
            ctx.status(204).render("");

            log.debug("CORS preflight approved for origin: {}", origin);
        }

        private void handleActualRequest(Context ctx, String origin) {
            if (!isOriginAllowed(origin)) {
                log.warn("CORS actual request rejected for origin: {}", origin);
                return;
            }

            // Set CORS headers for actual request
            setCorsHeaders(ctx, origin, false);

            log.debug("CORS headers set for actual request from origin: {}", origin);
        }

        private void setCorsHeaders(Context ctx, String origin, boolean isPreflight) {
            // Access-Control-Allow-Origin
            if (config.getAllowedOrigins().contains("*") && !config.isAllowCredentials()) {
                ctx.header("Access-Control-Allow-Origin", "*");
            } else if (origin != null && isOriginAllowed(origin)) {
                ctx.header("Access-Control-Allow-Origin", origin);
            }

            // Access-Control-Allow-Credentials
            if (config.isAllowCredentials()) {
                ctx.header("Access-Control-Allow-Credentials", "true");
            }

            // Headers specific to preflight requests
            if (isPreflight) {
                // Access-Control-Allow-Methods
                if (!config.getAllowedMethods().isEmpty()) {
                    String methods = String.join(", ", config.getAllowedMethods());
                    ctx.header("Access-Control-Allow-Methods", methods);
                }

                // Access-Control-Allow-Headers
                if (!config.getAllowedHeaders().isEmpty()) {
                    String headers = config.getAllowedHeaders().contains("*") ? "*" :
                            String.join(", ", config.getAllowedHeaders());
                    ctx.header("Access-Control-Allow-Headers", headers);
                }

                // Access-Control-Max-Age
                ctx.header("Access-Control-Max-Age", String.valueOf(config.getMaxAge()));
            } else {
                // Headers for actual requests
                // Access-Control-Expose-Headers
                if (!config.getExposedHeaders().isEmpty()) {
                    String exposedHeaders = String.join(", ", config.getExposedHeaders());
                    ctx.header("Access-Control-Expose-Headers", exposedHeaders);
                }
            }

            // Vary header to indicate that the response varies based on the Origin header
            if (!config.getAllowedOrigins().contains("*")) {
                String varyHeader = ctx.header("Vary");
                if (varyHeader == null || !varyHeader.contains("Origin")) {
                    ctx.header("Vary", varyHeader == null ? "Origin" : varyHeader + ", Origin");
                }
            }
        }

        private boolean isOriginAllowed(String origin) {
            if (origin == null) {
                return true; // Allow requests without origin (e.g., same-origin, mobile apps)
            }

            if (config.getAllowedOrigins().contains("*")) {
                return true;
            }

            return config.getAllowedOrigins().contains(origin) ||
                    config.getAllowedOrigins().stream()
                            .anyMatch(allowed -> matchesWildcard(origin, allowed));
        }

        private boolean areHeadersAllowed(String requestedHeaders) {
            if (config.getAllowedHeaders().contains("*")) {
                return true;
            }

            Set<String> requested = Arrays.stream(requestedHeaders.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());

            Set<String> allowed = config.getAllowedHeaders().stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());

            return allowed.containsAll(requested);
        }

        private boolean matchesWildcard(String origin, String pattern) {
            if (!pattern.contains("*")) {
                return origin.equals(pattern);
            }

            // Simple wildcard matching (supports * at the beginning)
            if (pattern.startsWith("*.")) {
                String domain = pattern.substring(2);
                return origin.endsWith("." + domain) || origin.equals(domain);
            }

            return false;
        }
    }

    // Handler interfaces
    @FunctionalInterface
    public interface RouteHandler {
        Context handle(Context ctx);

        default String getMethod() {
            return "GET";
        }
    }

    @FunctionalInterface
    public interface FilterHandler {
        void handle(Context ctx);
    }

    @FunctionalInterface
    public interface ExceptionHandler {
        void handle(Context ctx);
    }

    // Registry interface and implementation
    public interface Registry {

        <T> T get(Class<T> clazz);

        <T> void register(Class<T> clazz, T instance);

    }

    @Slf4j
    public static class ServerRegistry implements Registry {
        private final Map<Class<?>, Object> instances = new ConcurrentHashMap<>();

        public static ServerRegistry init(Consumer<ServerRegistry> configurator) {
            ServerRegistry registry = new ServerRegistry();
            configurator.accept(registry);
            return registry;
        }

        public ServerRegistry module(Class<?> moduleClass) {
            try {
                Object module = moduleClass.getDeclaredConstructor().newInstance();
                if (module instanceof ServerModule sm) {
                    sm.configure(this);
                }
            } catch (Exception e) {
                log.error("Failed to initialize module: {}", moduleClass.getName(), e);
            }

            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Class<T> clazz) {
            return (T) instances.get(clazz);
        }

        @Override
        public <T> void register(Class<T> clazz, T instance) {
            instances.put(clazz, instance);
        }
    }

    // Server configuration
    @Data
    @Builder
    public static class ServerConfig {

        @Builder.Default
        private int port = 8080;
        @Builder.Default
        private String uploadDir = System.getProperty("user.dir");
        @Builder.Default
        private CorsConfig corsConfig = CorsConfig.restrictive();

    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CorsConfig {

        @Builder.Default
        private Set<String> allowedOrigins = new HashSet<>(Arrays.asList("*"));

        @Builder.Default
        private Set<String> allowedMethods = new HashSet<>(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"
        ));

        @Builder.Default
        private Set<String> allowedHeaders = new HashSet<>(Arrays.asList(
                "Origin", "Content-Type", "Accept", "Authorization",
                "X-Requested-With", "X-API-Key", "Cache-Control"
        ));

        @Builder.Default
        private Set<String> exposedHeaders = new HashSet<>(Arrays.asList(
                "Content-Length", "Content-Range", "X-Total-Count"
        ));

        @Builder.Default
        private boolean allowCredentials = false;

        @Builder.Default
        private int maxAge = 86400; // 24 hours in seconds

        @Builder.Default
        private boolean enabled = true;

        // Predefined configurations
        public static CorsConfig permissive() {
            return CorsConfig.builder()
                    .allowedOrigins(Set.of("*"))
                    .allowedMethods(Set.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"))
                    .allowedHeaders(Set.of("*"))
                    .allowCredentials(false)
                    .maxAge(86400)
                    .build();
        }

        public static CorsConfig restrictive() {
            return CorsConfig.builder()
                    .allowedOrigins(Set.of())
                    .allowedMethods(Set.of("GET", "POST"))
                    .allowedHeaders(Set.of("Content-Type", "Accept"))
                    .allowCredentials(false)
                    .maxAge(300)
                    .build();
        }

        public static CorsConfig development() {
            return CorsConfig.builder()
                    .allowedOrigins(Set.of("http://localhost:3000", "http://localhost:8080",
                            "http://127.0.0.1:3000", "http://127.0.0.1:8080"))
                    .allowedMethods(Set.of("GET", "POST", "PUT", "DELETE", "OPTIONS"))
                    .allowedHeaders(Set.of("Origin", "Content-Type", "Accept", "Authorization"))
                    .allowCredentials(true)
                    .maxAge(3600)
                    .build();
        }

    }

    // Base dir utility
    public static class BaseDir {

        //TODO, redo or remove
        public static String find() {
            Path resourcesPath = Paths.get("src", "main", "resources");
            if (Files.exists(resourcesPath)) {
                return resourcesPath.toAbsolutePath().toString();
            }
            return System.getProperty("user.dir");
        }
    }

    // Route definition
    @Data
    @AllArgsConstructor
    public static class Route {
        private String method;
        private String path;
        private Pattern pattern;
        private List<String> paramNames;
        private RouteHandler handler;

        public static Route create(String method, String path, RouteHandler handler) {
            List<String> paramNames = new ArrayList<>();
            Pattern paramPattern = Pattern.compile("\\{([^}]+)}");
            Matcher matcher = paramPattern.matcher(path);
            StringBuilder regex = new StringBuilder();

            while (matcher.find()) {
                paramNames.add(matcher.group(1));
                matcher.appendReplacement(regex, "([^/]+)");
            }
            matcher.appendTail(regex);

            Pattern pattern = Pattern.compile("^" + regex + "$");
            return new Route(method, path, pattern, paramNames, handler);
        }

        public boolean matches(String method, String path) {
            return this.method.equals(method) && pattern.matcher(path).matches();
        }

        public Map<String, String> extractParams(String path) {
            Map<String, String> params = new HashMap<>();
            Matcher matcher = pattern.matcher(path);
            if (matcher.matches()) {
                for (int i = 0; i < paramNames.size(); i++) {
                    params.put(paramNames.get(i), matcher.group(i + 1));
                }
            }
            return params;
        }
    }

    // Chain handler
    @Slf4j
    public static class Chain {
        private final List<Route> routes = new ArrayList<>();
        private final List<FilterHandler> beforeFilters = new ArrayList<>();
        private final List<FilterHandler> afterFilters = new ArrayList<>();
        private ExceptionHandler exceptionHandler;
        private final Registry registry;
        private String prefix = "";
        private final CorsConfig corsConfig;
        private final CorsHandler corsHandler;

        //TODO redo to static method
        public Chain(Registry registry, CorsConfig corsConfig) {
            this.registry = registry;
            this.corsConfig = corsConfig;
            this.corsHandler = new CorsHandler(corsConfig);

            beforeFilters.addFirst(ctx -> corsHandler.handleCors(ctx));
        }

        public Chain get(String path, RouteHandler handler) {
            routes.add(Route.create("GET", prefix + path, handler));
            return this;
        }

        public Chain post(String path, RouteHandler handler) {
            routes.add(Route.create("POST", prefix + path, handler));
            return this;
        }

        public Chain put(String path, RouteHandler handler) {
            routes.add(Route.create("PUT", prefix + path, handler));
            return this;
        }

        public Chain delete(String path, RouteHandler handler) {
            routes.add(Route.create("DELETE", prefix + path, handler));
            return this;
        }

        public Chain path(String path, Class<?> handlerClass) {
            try {
                Object handler = registry.get(handlerClass);
                if (handler instanceof RouteHandler r) {
                    routes.add(Route.create(r.getMethod(), prefix + path, (RouteHandler) handler));
                }
            } catch (Exception e) {
                log.error("Failed to register path handler: {}", handlerClass.getName(), e);
            }
            return this;
        }

        //TODO remove
        public Chain prefix(String pathPrefix, Consumer<Chain> nested) {
            Chain nestedChain = new Chain(registry, corsConfig);
            nestedChain.prefix = this.prefix + "/" + pathPrefix;
            nested.accept(nestedChain);
            this.routes.addAll(nestedChain.routes);
            return this;
        }

        public static void files(Chain chain) {
            chain.get("/*", ctx -> {
                // Serve static files from configured directory
                StaticFileHandler.filesFrom(chain, BaseDir.find() + "/assets");
                return ctx;
            });
        }

        public Chain before(FilterHandler filter) {
            beforeFilters.add(filter);
            return this;
        }

        public Chain after(FilterHandler filter) {
            afterFilters.add(filter);
            return this;
        }

        public Chain exception(ExceptionHandler handler) {
            this.exceptionHandler = handler;
            return this;
        }

        public void handle(HttpExchange exchange) {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            Context ctx = Context.builder()
                    .exchange(exchange)
                    .registry(registry)
                    .build();

            try {
                // Execute before filters
                for (FilterHandler filter : beforeFilters) {
                    filter.handle(ctx);
                }

                // Find matching route
                Route matchedRoute = routes.stream()
                        .filter(route -> route.matches(method, path))
                        .findFirst()
                        .orElse(null);

                if (matchedRoute != null) {
                    ctx.setPathVariables(matchedRoute.extractParams(path));
                    Context response = matchedRoute.getHandler().handle(ctx);
                    if (response != null) {
                        ctx.send(response);
                    } else {
                        ctx.drain();
                    }
                } else {
                    ctx.status(404).render("Not Found");
                }

                // Execute after filters
                for (FilterHandler filter : afterFilters) {
                    filter.handle(ctx);
                }

            } catch (Exception e) {
                log.error("Request handling error", e);
                if (exceptionHandler != null) {
                    exceptionHandler.handle(ctx);
                } else {
                    ctx.status(500).render("Internal Server Error");
                }
            }
        }
    }

    // Server builder
    @Slf4j
    public static class ServerBuilder {

        private ServerConfig config = ServerConfig.builder().build();
        private Registry registry;
        private Chain chain;

        public ServerBuilder serverConfig(Function<ServerConfig.ServerConfigBuilder, ServerConfig.ServerConfigBuilder> configurator) {
            this.config = configurator.apply(ServerConfig.builder()).build();
            return this;
        }

        public ServerBuilder registry(Registry registry) {
            this.registry = registry;
            return this;
        }

        public ServerBuilder handlers(Consumer<Chain> chainConfig) {
            this.chain = new Chain(registry, config.getCorsConfig());
            chainConfig.accept(chain);
            return this;
        }

        public HttpServer build() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(config.getPort()), 0);
                server.createContext("/", chain::handle);
                server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
                return server;
            } catch (IOException e) {
                throw new RuntimeException("Failed to create server", e);
            }
        }

    }

    // Module interface
    public interface ServerModule {

        void configure(Registry registry);

    }

    public static class JacksonModule implements ServerModule {

        @Override
        public void configure(Registry registry) {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            registry.register(ObjectMapper.class, mapper);
        }

    }

    public static class StaticFileHandler {

        private static final Map<String, String> MIME_TYPES = new ConcurrentHashMap<>();
        private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
        private static final int BUFFER_SIZE = 8192;

        static {
            // Common MIME types
            MIME_TYPES.put("html", "text/html");
            MIME_TYPES.put("htm", "text/html");
            MIME_TYPES.put("css", "text/css");
            MIME_TYPES.put("js", "application/javascript");
            MIME_TYPES.put("json", "application/json");
            MIME_TYPES.put("xml", "application/xml");
            MIME_TYPES.put("txt", "text/plain");

            // Images
            MIME_TYPES.put("jpg", "image/jpeg");
            MIME_TYPES.put("jpeg", "image/jpeg");
            MIME_TYPES.put("png", "image/png");
            MIME_TYPES.put("gif", "image/gif");
            MIME_TYPES.put("bmp", "image/bmp");
            MIME_TYPES.put("webp", "image/webp");
            MIME_TYPES.put("svg", "image/svg+xml");
            MIME_TYPES.put("ico", "image/x-icon");

            // Fonts
            MIME_TYPES.put("woff", "font/woff");
            MIME_TYPES.put("woff2", "font/woff2");
            MIME_TYPES.put("ttf", "font/ttf");
            MIME_TYPES.put("eot", "application/vnd.ms-fontobject");
            MIME_TYPES.put("otf", "font/otf");

            // Audio/Video
            MIME_TYPES.put("mp3", "audio/mpeg");
            MIME_TYPES.put("wav", "audio/wav");
            MIME_TYPES.put("mp4", "video/mp4");
            MIME_TYPES.put("webm", "video/webm");
            MIME_TYPES.put("ogg", "audio/ogg");

            // Documents
            MIME_TYPES.put("pdf", "application/pdf");
            MIME_TYPES.put("doc", "application/msword");
            MIME_TYPES.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            MIME_TYPES.put("xls", "application/vnd.ms-excel");
            MIME_TYPES.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

            // Archives
            MIME_TYPES.put("zip", "application/zip");
            MIME_TYPES.put("tar", "application/x-tar");
            MIME_TYPES.put("gz", "application/gzip");
            MIME_TYPES.put("rar", "application/vnd.rar");

            // Default
            MIME_TYPES.put("", "application/octet-stream");
        }

        public static void files(Chain chain) {
            files(chain, "");
        }

        public static void files(Chain chain, String basePath) {
            chain.get("/*", ctx -> {
                try {
                    return handleStaticFile(ctx, basePath);
                } catch (Exception e) {
                    log.error("Error serving static file", e);
                    ctx.status(500);
                    return ctx;
                }
            });
        }

        private static Context handleStaticFile(Context ctx, String basePath) {
            String requestPath = ctx.getExchange().getRequestURI().getPath();

            // Remove leading slash and resolve relative to base path
            String relativePath = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;

            // Security check - prevent directory traversal
            if (relativePath.contains("..") || relativePath.contains("./") || relativePath.contains("\\")) {
                ctx.status(403);
                return ctx;
            }

            // Resolve file path
            Path filePath;
            if (basePath.isEmpty()) {
                // Use upload directory from server config
                String uploadDir = System.getProperty("user.dir"); // Default fallback
                filePath = Paths.get(uploadDir, relativePath);
            } else {
                filePath = Paths.get(basePath, relativePath);
            }

            // Check if file exists and is readable
            if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
                ctx.status(404);
                return ctx;
            }

            // Security check - ensure file is within allowed directory
            try {
                Path normalizedPath = filePath.normalize();
                Path allowedBasePath = basePath.isEmpty() ?
                        Paths.get(System.getProperty("user.dir")).normalize() :
                        Paths.get(basePath).normalize();

                if (!normalizedPath.startsWith(allowedBasePath)) {
                    ctx.status(403);
                    return ctx;
                }
            } catch (Exception e) {
                log.warn("Path security check failed for: {}", filePath, e);
                ctx.status(403);
                return ctx;
            }

            // Check if it's a directory
            if (Files.isDirectory(filePath)) {
                // Try to serve index.html if it exists
                Path indexFile = filePath.resolve("index.html");
                if (Files.exists(indexFile) && Files.isReadable(indexFile)) {
                    filePath = indexFile;
                } else {
                    ctx.status(404);
                    return ctx;
                }
            }

            try {
                // Check file size
                long fileSize = Files.size(filePath);
                if (fileSize > MAX_FILE_SIZE) {
                    log.warn("File too large: {} ({}MB)", filePath, fileSize / (1024 * 1024));
                    ctx.status(413); // Payload Too Large
                    return ctx;
                }

                // Get MIME type
                String mimeType = getMimeType(filePath);

                // Set headers
                ctx.header("Content-Type", mimeType);
                ctx.header("Content-Length", String.valueOf(fileSize));

                // Add caching headers for static assets
                if (isCacheableResource(mimeType)) {
                    ctx.header("Cache-Control", "public, max-age=86400"); // 1 day
                    ctx.header("Expires", ZonedDateTime.now(ZoneOffset.UTC)
                            .plusDays(1)
                            .format(DateTimeFormatter.RFC_1123_DATE_TIME));
                }

                // Handle conditional requests (If-Modified-Since)
                String ifModifiedSince = ctx.header("If-Modified-Since");
                long lastModified = Files.getLastModifiedTime(filePath).toMillis();

                if (ifModifiedSince != null) {
                    try {
                        long ifModifiedSinceTime = ZonedDateTime.parse(ifModifiedSince,
                                DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();

                        if (lastModified <= ifModifiedSinceTime) {
                            ctx.status(304); // Not Modified
                            return ctx;
                        }
                    } catch (Exception e) {
                        // Ignore invalid date format
                    }
                }

                // Set Last-Modified header
                ctx.header("Last-Modified", ZonedDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(lastModified), ZoneOffset.UTC)
                        .format(DateTimeFormatter.RFC_1123_DATE_TIME));

                // Handle range requests for large files (basic implementation)
                String rangeHeader = ctx.header("Range");
                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    return handleRangeRequest(ctx, filePath, rangeHeader, fileSize, mimeType);
                }

                // Read and serve file
                serveFile(ctx, filePath, mimeType);
                return ctx;
            } catch (IOException e) {
                log.error("Error reading file: {}", filePath, e);
                ctx.status(500);
                return ctx;
            }
        }

        private static String getMimeType(Path filePath) {
            String fileName = filePath.getFileName().toString();
            String extension = "";

            int lastDotIndex = fileName.lastIndexOf('.');
            if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
                extension = fileName.substring(lastDotIndex + 1).toLowerCase();
            }

            return MIME_TYPES.getOrDefault(extension, "application/octet-stream");
        }

        private static boolean isCacheableResource(String mimeType) {
            return mimeType.startsWith("image/") ||
                    mimeType.startsWith("font/") ||
                    mimeType.equals("text/css") ||
                    mimeType.equals("application/javascript") ||
                    mimeType.startsWith("audio/") ||
                    mimeType.startsWith("video/");
        }

        private static void serveFile(Context ctx, Path filePath, String mimeType) throws IOException {
            try (InputStream fileStream = Files.newInputStream(filePath);
                 OutputStream responseStream = ctx.getExchange().getResponseBody()) {

                // Send response headers
                ctx.getExchange().sendResponseHeaders(200, Files.size(filePath));

                // Stream file content
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = fileStream.read(buffer)) != -1) {
                    responseStream.write(buffer, 0, bytesRead);
                }
            }
        }

        private static Context handleRangeRequest(Context ctx, Path filePath, String rangeHeader,
                                                 long fileSize, String mimeType) {
            try {
                // Parse range header (basic implementation for single range)
                String range = rangeHeader.substring(6); // Remove "bytes="
                String[] parts = range.split("-");

                long start = 0;
                long end = fileSize - 1;

                if (parts.length >= 1 && !parts[0].isEmpty()) {
                    start = Long.parseLong(parts[0]);
                }
                if (parts.length >= 2 && !parts[1].isEmpty()) {
                    end = Long.parseLong(parts[1]);
                }

                // Validate range
                if (start >= fileSize || end >= fileSize || start > end) {
                    ctx.status(416); // Range Not Satisfiable
                    ctx.header("Content-Range", "bytes */" + fileSize);
                    return ctx;
                }

                long contentLength = end - start + 1;

                // Set partial content headers
                ctx.status(206); // Partial Content
                ctx.header("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
                ctx.header("Content-Length", String.valueOf(contentLength));
                ctx.header("Content-Type", mimeType);
                ctx.header("Accept-Ranges", "bytes");

                // Send partial content
                try (InputStream fileStream = Files.newInputStream(filePath);
                     OutputStream responseStream = ctx.getExchange().getResponseBody()) {

                    ctx.getExchange().sendResponseHeaders(206, contentLength);

                    // Skip to start position
                    fileStream.skip(start);

                    // Stream partial content
                    byte[] buffer = new byte[BUFFER_SIZE];
                    long remaining = contentLength;
                    int bytesRead;

                    while (remaining > 0 && (bytesRead = fileStream.read(buffer, 0,
                            (int) Math.min(buffer.length, remaining))) != -1) {
                        responseStream.write(buffer, 0, bytesRead);
                        remaining -= bytesRead;
                    }
                }

                return ctx;
            } catch (Exception e) {
                log.error("Error handling range request", e);
                ctx.status(500);
                return ctx;
            }
        }

        // Utility method to add MIME type
        public static void addMimeType(String extension, String mimeType) {
            MIME_TYPES.put(extension.toLowerCase(), mimeType);
        }

        // Enhanced Chain method with base directory support
        public static void filesFrom(Chain chain, String baseDirectory) {
            files(chain, baseDirectory);
        }

    }

}
