#!/usr/bin/env python3
"""
Flask Markdown to EPUB Converter

A web application that allows users to upload markdown files and convert them to EPUB using Pandoc.
"""

import os
import shutil
import subprocess
import uuid
from flask import Flask, render_template, request, redirect, url_for, flash, send_from_directory, jsonify, send_file
from typing import List, Optional
from werkzeug.utils import secure_filename

app = Flask(__name__)
app.config['UPLOAD_FOLDER'] = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'uploads')
app.config['OUTPUT_FOLDER'] = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'output')
app.config['MAX_CONTENT_LENGTH'] = 40 * 1024 * 1024  # 40 MB max upload size
app.config['ALLOWED_EXTENSIONS'] = {'md', 'markdown', 'txt'}
app.config['ALLOWED_IMAGE_EXTENSIONS'] = {'jpg', 'jpeg', 'png', 'gif'}

# Create upload and output directories if they don't exist
os.makedirs(app.config['UPLOAD_FOLDER'], exist_ok=True)
os.makedirs(app.config['OUTPUT_FOLDER'], exist_ok=True)


def allowed_file(filename, allowed_extensions):
    """Check if the uploaded file has an allowed extension."""
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in allowed_extensions


def convert_markdown_to_epub(
        input_files: List[str],
        output_file: str,
        title: Optional[str] = None,
        author: Optional[str] = None,
        cover_image: Optional[str] = None,
        toc_depth: int = 2
) -> bool:
    """
    Convert markdown files to EPUB using Pandoc.

    Args:
        input_files: List of markdown files to convert
        output_file: Name of the output EPUB file
        title: Title of the EPUB (optional)
        author: Author of the EPUB (optional)
        cover_image: Path to cover image file (optional)
        toc_depth: Depth of the table of contents (default: 2)

    Returns:
        True if conversion was successful, False otherwise
    """
    # Validate input files
    for file_path in input_files:
        if not os.path.exists(file_path):
            return False, f"Input file '{file_path}' does not exist."

    # Build the pandoc command
    cmd = ["pandoc"]

    # Add input files
    cmd.extend(input_files)

    # Output format and file
    cmd.extend(["-o", output_file])

    # EPUB specific options
    cmd.append("--verbose")
    cmd.append("--standalone")
    cmd.extend(["--toc", f"--toc-depth={toc_depth}"])

    # Add metadata if provided
    if title:
        cmd.extend(["--metadata", f"title={title}"])

    if author:
        cmd.extend(["--metadata", f"author={author}"])

    if cover_image and os.path.exists(cover_image):
        cmd.extend(["--epub-cover-image", cover_image])

    cmd.append("--wrap=preserve")
    # cmd.append("--embed-resources")

    try:
        # Run the pandoc command
        result = subprocess.run(cmd, check=True, capture_output=True, text=True)
        return True, "Conversion successful"
    except subprocess.CalledProcessError as e:
        error_message = f"Pandoc error: {e.stderr if e.stderr else str(e)}"
        return False, error_message
    except Exception as e:
        return False, f"Unexpected error: {str(e)}"


def check_pandoc_installed():
    """Check if pandoc is installed and available in the PATH."""
    try:
        subprocess.run(["pandoc", "--version"], check=True, capture_output=True)
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        return False


@app.route('/')
def index():
    """Render the main page of the application."""
    pandoc_installed = check_pandoc_installed()
    return render_template('index.html', pandoc_installed=pandoc_installed)


@app.route('/api/transform', methods=['POST'])
def api_transform():
    """API endpoint for direct conversion and EPUB download."""
    if 'markdown_files' not in request.files:
        return jsonify({'error': 'No files selected'}), 400

    files = request.files.getlist('markdown_files')

    if not files or files[0].filename == '':
        return jsonify({'error': 'No files selected'}), 400

    # Get form data
    title = request.form.get('title', '')
    author = request.form.get('author', '')
    toc_depth = int(request.form.get('toc_depth', 2))

    # Create a unique session ID for this conversion
    session_id = str(uuid.uuid4())
    session_upload_dir = os.path.join(app.config['UPLOAD_FOLDER'], session_id)
    os.makedirs(session_upload_dir, exist_ok=True)

    # Save the uploaded markdown files
    saved_files = []
    for file in files:
        if file and allowed_file(file.filename, app.config['ALLOWED_EXTENSIONS']):
            filename = secure_filename(file.filename)
            file_path = os.path.join(session_upload_dir, filename)
            file.save(file_path)
            saved_files.append(file_path)

    # Handle cover image if provided
    cover_image_path = None
    if 'cover_image' in request.files and request.files['cover_image'].filename != '':
        cover_image = request.files['cover_image']
        if allowed_file(cover_image.filename, app.config['ALLOWED_IMAGE_EXTENSIONS']):
            cover_filename = secure_filename(cover_image.filename)
            cover_image_path = os.path.join(session_upload_dir, cover_filename)
            cover_image.save(cover_image_path)

    if not saved_files:
        return jsonify({'error': 'No valid markdown files were uploaded'}), 400

    # Create output filename
    output_filename = secure_filename(request.form.get('output_filename', 'output.epub'))
    if not output_filename.endswith('.epub'):
        output_filename += '.epub'

    output_path = os.path.join(app.config['OUTPUT_FOLDER'], session_id)
    os.makedirs(output_path, exist_ok=True)
    output_file = os.path.join(output_path, output_filename)

    # Convert files to EPUB
    success, message = convert_markdown_to_epub(
        saved_files,
        output_file,
        title,
        author,
        cover_image_path,
        toc_depth
    )

    if success:
        # Return the file directly to the client
        try:
            response = send_file(output_file, as_attachment=True,
                                 download_name=output_filename)

            # Clean up the temporary files after sending
            @response.call_on_close
            def cleanup_files():
                try:
                    if os.path.exists(session_upload_dir):
                        shutil.rmtree(session_upload_dir)
                    if os.path.exists(output_path):
                        shutil.rmtree(output_path)
                except Exception as e:
                    app.logger.error(f"Error cleaning up files: {str(e)}")

            return response
        except Exception as e:
            return jsonify({'error': f'Error sending file: {str(e)}'}), 500
    else:
        # Clean up files on error
        try:
            if os.path.exists(session_upload_dir):
                shutil.rmtree(session_upload_dir)
            if os.path.exists(output_path):
                shutil.rmtree(output_path)
        except Exception as e:
            app.logger.error(f"Error cleaning up files: {str(e)}")

        return jsonify({'error': f'Conversion failed: {message}'}), 500


@app.route('/convert', methods=['POST'])
def convert():
    """Handle file uploads and conversion to EPUB."""
    if 'markdown_files' not in request.files:
        flash('No files selected', 'error')
        return redirect(request.url)

    files = request.files.getlist('markdown_files')

    if not files or files[0].filename == '':
        flash('No files selected', 'error')
        return redirect(request.url)

    # Get form data
    title = request.form.get('title', '')
    author = request.form.get('author', '')
    toc_depth = int(request.form.get('toc_depth', 2))

    # Create a unique session ID for this conversion
    session_id = str(uuid.uuid4())
    session_upload_dir = os.path.join(app.config['UPLOAD_FOLDER'], session_id)
    os.makedirs(session_upload_dir, exist_ok=True)

    # Save the uploaded markdown files
    saved_files = []
    for file in files:
        if file and allowed_file(file.filename, app.config['ALLOWED_EXTENSIONS']):
            filename = secure_filename(file.filename)
            file_path = os.path.join(session_upload_dir, filename)
            file.save(file_path)
            saved_files.append(file_path)

    # Handle cover image if provided
    cover_image_path = None
    if 'cover_image' in request.files and request.files['cover_image'].filename != '':
        cover_image = request.files['cover_image']
        if allowed_file(cover_image.filename, app.config['ALLOWED_IMAGE_EXTENSIONS']):
            cover_filename = secure_filename(cover_image.filename)
            cover_image_path = os.path.join(session_upload_dir, cover_filename)
            cover_image.save(cover_image_path)

    if not saved_files:
        flash('No valid markdown files were uploaded', 'error')
        return redirect(url_for('index'))

    # Create output filename
    output_filename = secure_filename(request.form.get('output_filename', 'output.epub'))
    if not output_filename.endswith('.epub'):
        output_filename += '.epub'

    output_path = os.path.join(app.config['OUTPUT_FOLDER'], session_id)
    os.makedirs(output_path, exist_ok=True)
    output_file = os.path.join(output_path, output_filename)

    # Convert files to EPUB
    success, message = convert_markdown_to_epub(
        saved_files,
        output_file,
        title,
        author,
        cover_image_path,
        toc_depth
    )

    if success:
        flash('Conversion successful!', 'success')
        return redirect(url_for('download_file', session_id=session_id, filename=output_filename))
    else:
        flash(f'Conversion failed: {message}', 'error')
        return redirect(url_for('index'))


@app.route('/download/<session_id>/<filename>')
def download_file(session_id, filename):
    """Serve the generated EPUB file for download."""
    output_dir = os.path.join(app.config['OUTPUT_FOLDER'], session_id)
    return render_template('download.html', session_id=session_id, filename=filename)


@app.route('/get_file/<session_id>/<filename>')
def get_file(session_id, filename):
    """Serve the actual file download."""
    output_dir = os.path.join(app.config['OUTPUT_FOLDER'], session_id)
    return send_from_directory(output_dir, filename, as_attachment=True)


@app.route('/cleanup/<session_id>')
def cleanup(session_id):
    """Clean up temporary files after download."""
    try:
        # Remove the upload and output directories for this session
        upload_dir = os.path.join(app.config['UPLOAD_FOLDER'], session_id)
        output_dir = os.path.join(app.config['OUTPUT_FOLDER'], session_id)

        if os.path.exists(upload_dir):
            shutil.rmtree(upload_dir)

        if os.path.exists(output_dir):
            shutil.rmtree(output_dir)

        return redirect(url_for('index'))
    except Exception as e:
        flash(f'Error cleaning up files: {str(e)}', 'error')
        return redirect(url_for('index'))


# Create templates directory and HTML files
# @app.before_first_request
# def create_templates():
#     templates_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'templates')
#     os.makedirs(templates_dir, exist_ok=True)
#
#     # Create index.html template
#     index_html = """
#
#     """
#
#     # Create download.html template
#     download_html = """
#
# #     """
#
#     # Write templates to files
#     with open(os.path.join(templates_dir, 'index.html'), 'w') as f:
#         f.write(index_html)
#
#     with open(os.path.join(templates_dir, 'download.html'), 'w') as f:
#         f.write(download_html)


if __name__ == '__main__':
    # Check for pandoc at startup
    pandoc_installed = check_pandoc_installed()
    if not pandoc_installed:
        print("WARNING: Pandoc is not installed or not found in PATH.")
        print("Please install Pandoc from https://pandoc.org/installing.html")

    # Start the Flask app - listen on all interfaces for Docker
    app.run(debug=False, host='0.0.0.0')
