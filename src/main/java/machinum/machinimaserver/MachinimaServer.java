package machinum.machinimaserver;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
@RequiredArgsConstructor
public class MachinimaServer {

    @Getter
    private final HttpServer httpServer;

    @SneakyThrows
    public static void startAndWait(Consumer<ServerBuilder> configurator) {
        var serverRunning = new AtomicBoolean(true);
        var future = start(configurator);
        var httpServer = future.get();

        // Add shutdown hook
        var hook = new Thread(() -> {
            log.info("Shutdown hook triggered");
            gracefulShutdown(httpServer, serverRunning);
        });

        try {
            Runtime.getRuntime().addShutdownHook(hook);

            // Keep main thread alive and run periodic tasks
            runMainLoop(serverRunning);
        } finally {
            Runtime.getRuntime().removeShutdownHook(hook);
        }
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

    private static void gracefulShutdown(MachinimaServer server, AtomicBoolean serverRunning) {
        log.info("Initiating graceful shutdown...");
        serverRunning.set(false);

        try {
            // Stop accepting new requests
            if (server.getHttpServer() != null) {
                server.stop(); // Wait up to 5 seconds for ongoing requests
                log.info("HTTP server stopped");
            }

            log.info("Graceful shutdown completed");
        } catch (Exception e) {
            log.error("Error during shutdown", e);
        }

        System.exit(0);
    }

    public static CompletableFuture<MachinimaServer> start(Consumer<ServerBuilder> configurator) {
        return CompletableFuture.supplyAsync(() -> {
            var builder = new ServerBuilder();
            configurator.accept(builder);
            int port = builder.config.getPort();
            var server = new MachinimaServer(builder.build());
            server.start();

            log.info("Server started on port {}", port);
            return server;
        });
    }

    public MachinimaServer start() {
        this.httpServer.start();
        return this;
    }

    public MachinimaServer stop() {
        this.httpServer.stop(5);
        return this;
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

        private SessionStorage sessionStorage;
        private HttpExchange exchange;
        private Map<String, String> pathVariables;
        private Registry registry;
        private final AtomicReference<Object> result = new AtomicReference<>(null);
        private final AtomicReference<Integer> responseStatus = new AtomicReference<>(-1);

        public String url() {
            return exchange.getRequestURI().toString();
        }

        public String method() {
            return exchange.getRequestMethod();
        }

        public String path(String name) {
            return pathVariables.getOrDefault(name, "");
        }

        public String queryParam(String name) {
            String query = exchange.getRequestURI().getQuery();
            if (query == null || query.isBlank()) return "";

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
            if(responseStatus.get().equals(-1)) {
                responseStatus.getAndSet(404);
            }

            return doSend("");
        }
        
        public Context send(Context context) {
            Object data = context.getResult().get();

            try {
                if (data instanceof String s) {
                    if(!exchange.getResponseHeaders().containsKey(CONTENT_TYPE)) {
                        header(CONTENT_TYPE, CONTENT_TYPE_TEXT);
                    }
                    return doSend(s);
                } else if(Objects.nonNull(data)){
                    ObjectMapper mapper = registry.get(ObjectMapper.class);
                    String json = mapper.writeValueAsString(data);
                    return doSend(json);
                } else {
                    return doSend("");
                }
            } catch (Exception e) {
                return status(500).doSend("{ \"error\": \"Internal server error\" }");
            }
        }

        private Context doSend(String data) {
            try {
                if(!exchange.getResponseHeaders().containsKey(CONTENT_TYPE)) {
                    header(CONTENT_TYPE, CONTENT_TYPE_JSON);
                }

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

        public SessionStorage session() {
            return sessionStorage;
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
            log.trace("Handling CORS preflight request from origin: {}", origin);

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

            log.trace("CORS preflight approved for origin: {}", origin);
        }

        private void handleActualRequest(Context ctx, String origin) {
            if (!isOriginAllowed(origin)) {
                log.warn("CORS actual request rejected for origin: {}", origin);
                return;
            }

            // Set CORS headers for actual request
            setCorsHeaders(ctx, origin, false);

            log.trace("CORS headers set for actual request from origin: {}", origin);
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

        Context handle(Context ctx);

        static ExceptionHandler defaultOne() {
            return ctx -> ctx.status(500).render("Internal Server Error");
        }

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

    public interface SessionStorage {

        static SessionStorage defaultOne() {
            return new MapSessionStorage();
        }

        static SessionStorage empty() {
            return new NoopSessionStorage();
        }

        SessionStorage add(String key, Object value);

        SessionStorage update(String key, Object value);

        SessionStorage delete(String key);

        <U> Optional<U> find(String key);

        default <U> U get(String key) {
            return (U) find(key)
                    .orElseThrow(() -> new RuntimeException("Value[%s] for given key is not found in session".formatted(key)));
        }

        boolean exists(String key);

        boolean contains(String key, String value);

        default SessionStorage customize(Function<SessionStorage, SessionStorage> customizer) {
            return customizer.apply(this);
        }

    }

    public static class NoopSessionStorage implements SessionStorage {

        @Override
        public NoopSessionStorage add(String key, Object value) {
            // No-op
            return this;
        }

        @Override
        public NoopSessionStorage update(String key, Object value) {
            // No-op
            return this;
        }

        @Override
        public NoopSessionStorage delete(String key) {
            // No-op
            return this;
        }

        @Override
        public <U> Optional<U> find(String key) {
            return Optional.empty();
        }

        @Override
        public boolean exists(String key) {
            return false;
        }

        @Override
        public boolean contains(String key, String value) {
            return false;
        }

    }

    public static class MapSessionStorage implements SessionStorage {

        private final Map<String, Object> storage = new ConcurrentHashMap<>();

        @Override
        public MapSessionStorage add(String key, Object value) {
            storage.putIfAbsent(key, value);
            return this;
        }

        @Override
        public MapSessionStorage update(String key, Object value) {
            storage.put(key, value);
            return this;
        }

        @Override
        public MapSessionStorage delete(String key) {
            storage.remove(key);
            return this;
        }

        @Override
        public <U> Optional<U> find(String key) {
            return Optional.ofNullable((U) storage.get(key));
        }

        @Override
        public boolean exists(String key) {
            return storage.containsKey(key);
        }

        @Override
        public boolean contains(String key, String value) {
            return exists(key) && Objects.equals(storage.get(key), value);
        }

    }

    // Base dir utility
    @Deprecated(forRemoval = true)
    public static class BaseDir {

        //TODO, redo or remove
        @Deprecated(forRemoval = true)
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
        private final Registry registry;
        private ExceptionHandler exceptionHandler = ExceptionHandler.defaultOne();

        public Chain(Registry registry, CorsConfig corsConfig) {
            this.registry = registry;

            var corsHandler = new CorsHandler(corsConfig);
            this.beforeFilters.addFirst(corsHandler::handleCors);
        }

        public Chain get(String path, RouteHandler handler) {
            routes.add(Route.create("GET", path, handler));
            return this;
        }

        public Chain post(String path, RouteHandler handler) {
            routes.add(Route.create("POST", path, handler));
            return this;
        }

        public Chain put(String path, RouteHandler handler) {
            routes.add(Route.create("PUT", path, handler));
            return this;
        }

        public Chain delete(String path, RouteHandler handler) {
            routes.add(Route.create("DELETE", path, handler));
            return this;
        }

        public Chain path(String path, Class<?> handlerClass) {
            try {
                var handler = registry.get(handlerClass);
                if (handler instanceof RouteHandler r) {
                    routes.add(Route.create(r.getMethod(), path, (RouteHandler) handler));
                }
            } catch (Exception e) {
                log.error("Failed to register path handler: {}", handlerClass.getName(), e);
            }
            return this;
        }

        public Chain files(String folder) {
            return StaticFileHandler.files(this, folder);
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

        public void handle(HttpExchange exchange, SessionStorage sessionStorage) {
            var method = exchange.getRequestMethod();
            var path = exchange.getRequestURI().getPath();

            var ctx = Context.builder()
                    .sessionStorage(sessionStorage)
                    .exchange(exchange)
                    .registry(registry)
                    .build();

            try {
                // Execute before filters
                for (var filter : beforeFilters) {
                    filter.handle(ctx);
                }

                // Find matching route
                var matchedRoute = routes.stream()
                        .filter(route -> route.matches(method, path))
                        .findFirst()
                        .orElse(null);

                if (matchedRoute != null) {
                    ctx.setPathVariables(matchedRoute.extractParams(path));
                    var response = matchedRoute.getHandler().handle(ctx);
                    if (response != null) {
                        ctx.send(response);
                    } else {
                        //fallback if user forgot to return ctx instance
                        ctx.drain();
                    }
                } else {
                    ctx.send(ctx.status(404).render("Not Found"));
                }

                // Execute after filters
                for (var filter : afterFilters) {
                    filter.handle(ctx);
                }

            } catch (Exception e) {
                log.error("Request handling error", e);
                ctx.send(exceptionHandler.handle(ctx));
            }
        }

    }

    // Server builder
    @Slf4j
    public static class ServerBuilder {

        private ServerConfig config = ServerConfig.builder().build();
        private Registry registry;
        private Chain chain;
        private SessionStorage sessionStorage = SessionStorage.empty();
        private Runnable startAction = () -> {};

        public ServerBuilder serverConfig(Function<ServerConfig.ServerConfigBuilder, ServerConfig.ServerConfigBuilder> configurator) {
            this.config = configurator.apply(ServerConfig.builder()).build();
            return this;
        }

        public ServerBuilder sessionStorage(SessionStorage sessionStorage) {
            this.sessionStorage = sessionStorage;
            return this;
        }

        public ServerBuilder registry(Registry registry) {
            this.registry = registry;
            return this;
        }

        public ServerBuilder registry(Consumer<ServerRegistry> configurator) {
            return registry(ServerRegistry.init(configurator));
        }

        public ServerBuilder handlers(Consumer<Chain> chainConfig) {
            this.chain = new Chain(registry, config.getCorsConfig());
            chainConfig.accept(chain);
            return this;
        }

        public ServerBuilder onStarted(Runnable runnable) {
            this.startAction = runnable;
            return this;
        }

        public HttpServer build() {
            try {
                //TODO add creator/builder for session
                var storage = sessionStorage;
                var server = HttpServer.create(new InetSocketAddress(config.getPort()), 0);
                server.createContext("/", exchange -> chain.handle(exchange, storage));
                server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
                startAction.run();
                return server;
            } catch (IOException e) {
                return ExceptionUtils.rethrow(e);
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

}
