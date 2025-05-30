#!/usr/bin/env python3
"""
Flask Markdown to EPUB Converter

A web application that allows users to upload markdown files and convert them to EPUB using Pandoc.
"""

import os
import shutil
import subprocess
import tempfile
import uuid
import yaml
from datetime import datetime
from flask import Flask, render_template, request, redirect, url_for, flash, send_from_directory, jsonify, send_file
from typing import List, Optional, Dict, Any, Tuple
from werkzeug.utils import secure_filename

app = Flask(__name__)
app.config['UPLOAD_FOLDER'] = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'uploads')
app.config['OUTPUT_FOLDER'] = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'output')
app.config['APP_PANDOC_TEMPLATES'] = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'epub')
app.config['MAX_CONTENT_LENGTH'] = 40 * 1024 * 1024  # 40 MB max upload size
app.config['ALLOWED_EXTENSIONS'] = {'md', 'markdown', 'txt'}
app.config['APP_DATE_FORMAT'] = '%Y-%m-%d'
app.config['ALLOWED_IMAGE_EXTENSIONS'] = {'jpg', 'jpeg', 'png', 'gif'}
app.secret_key = "super secret key"

# Create upload and output directories if they don't exist
os.makedirs(app.config['UPLOAD_FOLDER'], exist_ok=True)
os.makedirs(app.config['OUTPUT_FOLDER'], exist_ok=True)


class EpubMetadata:
    """Class to handle EPUB metadata with validation and defaults."""

    def __init__(self, **kwargs):
        self.title = kwargs.get('title', 'Безымянный')
        self.subtitle = kwargs.get('subtitle', '')
        self.author = kwargs.get('author', 'Unknown Author')
        self.publisher = kwargs.get('publisher', '')
        self.language = kwargs.get('language', 'ru-RU')
        self.date = self._parse_date(kwargs.get('date', ''))
        self.pubdate = self._parse_date(kwargs.get('pubdate', ''))
        self.description = kwargs.get('description', '')
        self.rights = kwargs.get('rights', '')
        self.keywords = self._parse_keywords(kwargs.get('keywords', ''))
        self.toc_depth = max(1, min(6, int(kwargs.get('toc_depth', 2))))
        self.cover_image = kwargs.get('cover_image')
        # custom fields
        self.legal_rights = kwargs.get('legal_rights', '')
        self.title_prefix = kwargs.get('title_prefix', '')
        self.publisher_info = kwargs.get('publisher_info', '')
        self.edition = kwargs.get('edition', '')
        self.social_links = kwargs.get('social_links', [])
        self.website = kwargs.get('website', '')
        self.disclaimer = kwargs.get('disclaimer', '')

    def _parse_date(self, date_str: str) -> str:
        """Parse and validate date string."""
        if not date_str:
            return datetime.now().strftime('%Y-%m-%d')
        try:
            parsed_date = datetime.strptime(date_str.strip(), '%Y-%m-%d')
            return parsed_date.strftime('%Y-%m-%d')
        except ValueError:
            return datetime.now().strftime('%Y-%m-%d')

    def _parse_keywords(self, keywords_str: str) -> List[str]:
        """Parse and clean keywords."""
        if not keywords_str:
            return []
        return [kw.strip() for kw in keywords_str.split(',') if kw.strip()]

    def to_pandoc_metadata(self) -> Dict[str, Any]:
        """Convert to pandoc metadata format."""
        metadata = {
            'title': self.title,
            'author': self.author,
            'lang': self.language,
            'date': self.date,
        }

        if self.subtitle:
            metadata['subtitle'] = self.subtitle
        if self.publisher:
            metadata['publisher'] = self.publisher
        if self.pubdate:
            metadata['pubdate'] = self.pubdate
        if self.description:
            metadata['description'] = self.description
        if self.rights:
            metadata['rights'] = self.rights
        if self.keywords:
            metadata['keywords'] = self.keywords
        if self.cover_image:
            metadata['cover-image'] = self.cover_image
        # Custom fields
        if self.legal_rights:
            metadata['legal-rights'] = self.legal_rights
        if self.title_prefix:
            metadata['title-prefix'] = self.title_prefix
        if self.publisher_info:
            metadata['publisher-info'] = self.publisher_info
        if self.edition:
            metadata['edition'] = self.edition
        if self.social_links:
            metadata['social-links'] = self.social_links
        if self.website:
            metadata['website'] = self.website
        if self.disclaimer:
            metadata['disclaimer'] = self.disclaimer

        return metadata


class EpubConverter:
    """Secure EPUB converter using pypandoc."""

    def __init__(self, upload_folder: str, output_folder: str, template_folder: str, logger):
        self.upload_folder = upload_folder
        self.output_folder = output_folder
        self.allowed_md_extensions = {'md', 'markdown', 'txt'}
        self.allowed_img_extensions = {'jpg', 'jpeg', 'png', 'gif', 'webp'}
        self.epub_template = os.path.join(template_folder, 'custom-epub-template.html')
        self.css_template = os.path.join(template_folder, 'template.css')
        self.logger = logger

    def _is_file_allowed(self, filename: str, allowed_extensions: set) -> bool:
        """Check if file extension is allowed."""
        return '.' in filename and \
            filename.rsplit('.', 1)[1].lower() in allowed_extensions

    def _create_metadata_file(self, metadata: EpubMetadata, temp_dir: str) -> str:
        """Create YAML metadata file."""
        metadata_file = os.path.join(temp_dir, 'metadata.yaml')
        with open(metadata_file, 'w', encoding='utf-8') as f:
            yaml.dump(metadata.to_pandoc_metadata(), f,
                      default_flow_style=False, allow_unicode=True)
        return metadata_file

    def convert_to_epub(self, input_files: List[str], metadata: EpubMetadata,
                        output_filename: str) -> Tuple[bool, str, Optional[str]]:
        """
        Convert markdown files to EPUB using pypandoc.

        Returns:
            Tuple of (success, message, output_file_path)
        """
        session_id = str(uuid.uuid4())
        self.logger.info(f'Starting conversion with session ID: {session_id} for {len(input_files)} files')

        with tempfile.TemporaryDirectory() as temp_dir:
            try:
                # Validate input files
                for file_path in input_files:
                    if not os.path.exists(file_path):
                        self.logger.error(f"Input file '{file_path}' does not exist")
                        return False, f"Input file '{file_path}' does not exist", None

                # Create metadata file
                metadata_file = self._create_metadata_file(metadata, temp_dir)
                self.logger.debug(f'Metadata file created at {metadata_file}')

                # Prepare output path
                session_output_dir = os.path.join(self.output_folder, session_id)
                os.makedirs(session_output_dir, exist_ok=True)
                self.logger.debug(f'Session output directory created at {session_output_dir}')

                if not output_filename.endswith('.epub'):
                    output_filename += '.epub'

                output_file = os.path.join(session_output_dir, secure_filename(output_filename))
                self.logger.debug(f'Output file path: {output_file}')

                # Build pandoc command
                cmd = ["pandoc"]
                cmd.extend(input_files)
                cmd.extend(["--output", output_file])
                cmd.extend([
                    "--verbose",
                    "--standalone",
                    "--embed-resources",
                    "--toc",
                    f"--toc-depth={metadata.toc_depth}",
                    f"--metadata-file={metadata_file}",
                    "--wrap=preserve"
                ])

                # Use custom template if available
                if os.path.exists(self.epub_template):
                    cmd.extend(["--template", self.epub_template])

                # Use custom template of css if available
                if os.path.exists(self.css_template):
                    cmd.extend([f"--css={self.css_template}"])

                # Add cover image if provided
                if metadata.cover_image and os.path.exists(metadata.cover_image):
                    cmd.extend(["--epub-cover-image", metadata.cover_image])
                    self.logger.debug(f'Cover image added: {metadata.cover_image}')

                # Execute pandoc command
                self.logger.debug(f'Executing pandoc command: {" ".join(cmd)}')
                result = subprocess.run(
                    cmd,
                    check=True,
                    capture_output=True,
                    text=True,
                    cwd=temp_dir
                )
                self.logger.info(f'Pandoc command executed successfully:\n{result.stdout}')

                return True, "Conversion successful", output_file

            except subprocess.CalledProcessError as e:
                # Clean up on error
                if 'session_output_dir' in locals() and os.path.exists(session_output_dir):
                    shutil.rmtree(session_output_dir, ignore_errors=True)
                    self.logger.error(f'Cleaned up session directory: {session_output_dir}')
                error_message = f"Pandoc error: {e.stderr if e.stderr else str(e)}"
                self.logger.error(error_message)
                return False, error_message, None
            except Exception as e:
                # Clean up on error
                if 'session_output_dir' in locals() and os.path.exists(session_output_dir):
                    shutil.rmtree(session_output_dir, ignore_errors=True)
                    self.logger.error(f'Cleaned up session directory: {session_output_dir}')
                error_message = f"Conversion failed: {str(e)}"
                self.logger.error(error_message)
                return False, error_message, None


def check_pandoc_installed():
    """Check if pandoc is installed and available in the PATH."""
    try:
        subprocess.run(["pandoc", "--version"], check=True, capture_output=True)
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        return False


@app.route('/favicon.ico')
def favicon():
    """Serve the favicon from the static folder"""
    return send_from_directory('static', 'favicon.ico', mimetype='image/vnd.microsoft.icon')


@app.route('/pandoc')
@app.route('/')
def index():
    """Render the main page of the application."""
    pandoc_installed = check_pandoc_installed()
    return render_template('index.html', pandoc_installed=pandoc_installed)


@app.route('/api/transform', methods=['POST'])
def api_transform():
    """API endpoint for markdown to EPUB conversion."""
    app.logger.info('Received request at /api/transform')

    # Validate file upload
    if 'markdown_files' not in request.files:
        app.logger.error('No markdown files provided')
        return jsonify({'error': 'No markdown files provided'}), 400

    files = request.files.getlist('markdown_files')
    if not files or all(f.filename == '' for f in files):
        app.logger.error('No valid files selected')
        return jsonify({'error': 'No valid files selected'}), 400

    # Initialize converter
    converter = EpubConverter(
        upload_folder=app.config['UPLOAD_FOLDER'],
        output_folder=app.config['OUTPUT_FOLDER'],
        template_folder=app.config['APP_PANDOC_TEMPLATES'],
        logger=app.logger
    )
    app.logger.debug(
        f'Initialized EpubConverter with upload folder: {converter.upload_folder} and output folder: {converter.output_folder}')

    # Create session directory
    session_id = str(uuid.uuid4())
    session_upload_dir = os.path.join(app.config['UPLOAD_FOLDER'], session_id)
    os.makedirs(session_upload_dir, exist_ok=True)
    app.logger.debug(f'Created session upload directory: {session_upload_dir}')

    try:
        # Save markdown files
        saved_files = []
        for file in files:
            if file and converter._is_file_allowed(file.filename, converter.allowed_md_extensions):
                filename = secure_filename(file.filename)
                file_path = os.path.join(session_upload_dir, filename)
                file.save(file_path)
                saved_files.append(file_path)

        if not saved_files:
            app.logger.error('No valid markdown files uploaded')
            return jsonify({'error': 'No valid markdown files uploaded'}), 400

        # Handle cover image
        cover_image_path = None
        if 'cover_image' in request.files and request.files['cover_image'].filename:
            cover_image = request.files['cover_image']
            if converter._is_file_allowed(cover_image.filename, converter.allowed_img_extensions):
                cover_filename = secure_filename(cover_image.filename)
                cover_image_path = os.path.join(session_upload_dir, cover_filename)
                cover_image.save(cover_image_path)

        # Create metadata object with all form parameters
        metadata = EpubMetadata(
            title=request.form.get('title', ''),
            subtitle=request.form.get('subtitle', ''),
            author=request.form.get('author', ''),
            publisher=request.form.get('publisher', ''),
            language=request.form.get('language', 'ru-RU'),
            date=request.form.get('date', ''),
            pubdate=request.form.get('pubdate', ''),
            description=request.form.get('description', ''),
            rights=request.form.get('rights', ''),
            keywords=request.form.get('keywords', ''),
            toc_depth=request.form.get('toc_depth', 2),
            cover_image=cover_image_path
        )
        app.logger.debug(f'Created metadata: {metadata}')

        # Get output filename
        output_filename = request.form.get('output_filename', 'output.epub')
        app.logger.debug(f'Output filename set to: {output_filename}')

        # Convert to EPUB
        success, message, output_file = converter.convert_to_epub(
            saved_files, metadata, output_filename
        )
        if success and output_file:
            app.logger.info(f'EPUB conversion successful for session ID: {session_id}, output file: {output_file}')
            # Send file to client
            response = send_file(
                output_file,
                as_attachment=True,
                download_name=os.path.basename(output_file)
            )

            # Clean up after response
            @response.call_on_close
            def cleanup_files():
                try:
                    if os.path.exists(session_upload_dir):
                        shutil.rmtree(session_upload_dir)
                    if os.path.exists(os.path.dirname(output_file)):
                        shutil.rmtree(os.path.dirname(output_file))
                except Exception as e:
                    app.logger.error(f"Cleanup error: {e}")

            return response
        else:
            app.logger.error(f'EPUB conversion failed for session ID: {session_id} with message: {message}')
            return jsonify({'error': message}), 500

    except Exception as e:
        app.logger.exception(f'Processing error in /api/transform for session ID: {session_id}')
        return jsonify({'error': f'Processing error: {str(e)}'}), 500

    finally:
        # Ensure cleanup on any exception
        if os.path.exists(session_upload_dir):
            shutil.rmtree(session_upload_dir, ignore_errors=True)
            app.logger.debug(f'Session upload directory cleaned up: {session_upload_dir}')


@app.route('/convert', methods=['POST'])
def convert():
    """Handle file uploads and conversion to EPUB using new API."""
    try:
        # Validate file upload
        if 'markdown_files' not in request.files:
            app.logger.error('No files selected')
            flash('No files selected', 'error')
            return redirect(request.url)

        files = request.files.getlist('markdown_files')
        if not files or all(f.filename == '' for f in files):
            app.logger.error('No valid files uploaded')
            flash('No files selected', 'error')
            return redirect(request.url)

        app.logger.info(f"Got request to create epub from: {len(files)} files")

        # Initialize converter
        converter = EpubConverter(
            upload_folder=app.config['UPLOAD_FOLDER'],
            output_folder=app.config['OUTPUT_FOLDER'],
            template_folder=app.config['APP_PANDOC_TEMPLATES'],
            logger=app.logger
        )

        # Create session directory
        session_id = str(uuid.uuid4())
        session_upload_dir = os.path.join(app.config['UPLOAD_FOLDER'], session_id)
        os.makedirs(session_upload_dir, exist_ok=True)

        # Save markdown files
        saved_files = []
        for file in files:
            if file and converter._is_file_allowed(file.filename, converter.allowed_md_extensions):
                filename = secure_filename(file.filename)
                file_path = os.path.join(session_upload_dir, filename)
                file.save(file_path)
                saved_files.append(file_path)

        if not saved_files:
            app.logger.error('No valid markdown files were uploaded')
            flash('No valid markdown files were uploaded', 'error')
            return redirect(url_for('index'))

        # Handle cover image
        cover_image_path = None
        if 'cover_image' in request.files and request.files['cover_image'].filename:
            cover_image = request.files['cover_image']
            if converter._is_file_allowed(cover_image.filename, converter.allowed_img_extensions):
                cover_filename = secure_filename(cover_image.filename)
                cover_image_path = os.path.join(session_upload_dir, cover_filename)
                cover_image.save(cover_image_path)

        # Handle social_links array
        social_links = request.form.getlist('social_links')
        # Filter out empty strings
        social_links = [link.strip() for link in social_links if link.strip()]

        # Create metadata object with form parameters
        metadata = EpubMetadata(
            title=request.form.get('title', 'Unnamed'),
            subtitle=request.form.get('subtitle', ''),
            author=request.form.get('author', ''),
            publisher=request.form.get('publisher', ''),
            language=request.form.get('language', 'ru-RU'),
            date=request.form.get('date', ''),
            pubdate=request.form.get('pubdate', ''),
            description=request.form.get('description', ''),
            rights=request.form.get('rights', ''),
            keywords=request.form.get('keywords', ''),
            toc_depth=request.form.get('toc_depth', 2),
            cover_image=cover_image_path,
            # custom fields
            title_prefix=request.form.get('title_prefix', ''),
            publisher_info=request.form.get('publisher_info', ''),
            edition=request.form.get('edition', ''),
            legal_rights=request.form.get('legal_rights', ''),
            social_links=social_links,
            website=request.form.get('website', '')
        )

        # Get output filename
        output_filename = request.form.get('output_filename', 'output.epub')

        # Convert to EPUB using new API
        success, message, output_file = converter.convert_to_epub(
            saved_files, metadata, output_filename
        )

        if success and output_file:
            app.logger.info('Conversion successful')
            flash('Conversion successful!', 'success')
            # Extract session_id from output_file path for download
            output_session_id = os.path.basename(os.path.dirname(output_file))
            return redirect(url_for('download_file',
                                    session_id=output_session_id,
                                    filename=os.path.basename(output_file)))
        else:
            app.logger.error(f'Conversion failed: {message}')
            flash(f'Conversion failed: {message}', 'error')
            return redirect(url_for('index'))

    except Exception as e:
        app.logger.exception('Processing error')
        flash(f'Processing error: {str(e)}', 'error')
        return redirect(url_for('index'))

    finally:
        # Clean up upload directory
        if os.path.exists(session_upload_dir):
            shutil.rmtree(session_upload_dir, ignore_errors=True)


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


if __name__ == '__main__':
    # Check for pandoc at startup
    pandoc_installed = check_pandoc_installed()
    if not pandoc_installed:
        print("WARNING: Pandoc is not installed or not found in PATH.")
        print("Please install Pandoc from https://pandoc.org/installing.html")

    # Start the Flask app - listen on all interfaces for Docker
    app.run(debug=True, host='0.0.0.0')
