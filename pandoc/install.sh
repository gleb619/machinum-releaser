#!/bin/bash

echo "Installing"

python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install flask

echo "Starting"

python app.py