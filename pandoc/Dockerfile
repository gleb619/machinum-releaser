# Dockerfile for Flask Markdown to EPUB Converter
FROM python:3.9-slim

# Install pandoc
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    wget dpkg \
    && wget https://github.com/jgm/pandoc/releases/download/3.7.0.1/pandoc-3.7.0.1-1-amd64.deb  \
    && dpkg -i pandoc-3.7.0.1-1-amd64.deb \
    && rm pandoc-3.7.0.1-1-amd64.deb \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy requirements first to leverage Docker cache
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Create directories for uploads and output
RUN mkdir -p uploads output templates epub

# Expose port
EXPOSE 5000

# Run as non-root user for better security
RUN useradd -m appuser
RUN chown -R appuser:appuser /app
USER appuser

# Set environment variables
ENV FLASK_APP=app.py
ENV FLASK_ENV=production

# Command to run the application
CMD ["python", "app.py"]