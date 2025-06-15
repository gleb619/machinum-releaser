export function editApp() {
  return {
    drawerOpen: false,
    drawerMode: 'view',
    isUploading: false,
    genreInput: '',
    tagInput: '',
    newBook: {},
    showImportModal: false,
    remoteUrl: '',

    openCreateDrawer() {
      this.resetNewBook();
      this.drawerMode = 'edit';
      this.drawerOpen = true;
    },
  
    resetNewBook() {
      this.newBook = {
        ruName: '',
        enName: '',
        originName: '',
        link: '',
        linkText: '',
        type: '',
        genre: [],
        tags: [],
        year: null,
        chapters: null,
        author: '',
        description: '',
        imageId: '00000000-0000-0000-0000-000000000000',
        originImageId: '00000000-0000-0000-0000-000000000000',
      };
      this.genreInput = '';
      this.tagInput = '';
    },
  
    async saveBook() {
      if (this.isUploading) {
        this.showToast('Please wait for the image to finish uploading', 'warning');
        return;
      }

      // Process comma-separated inputs
      if (this.genreInput) {
        this.newBook.genre = this.genreInput.split(',').map(item => item.trim()).filter(item => item);
      }
  
      if (this.tagInput) {
        this.newBook.tags = this.tagInput.split(',').map(item => item.trim()).filter(item => item);
      }

      const isUpdate = this.newBook.id !== undefined;
      const url = isUpdate ? `/api/books/${this.newBook.id}` : '/api/books';
      const method = isUpdate ? 'PUT' : 'POST';
  
      try {
        const response = await fetch(url, {
          method: method,
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(this.newBook)
        });
  
        if (!response.ok) {
            response.json()
            .then(rsp => {
                this.showToast(`Failed to persist book: ${rsp.message || rsp.detail}`, true);
            });

          throw new Error('Failed to persist book');
        }
  
        await this.fetchBooks();
        this.drawerOpen = false;
        this.showToast('Book changed successfully!');
      } catch (error) {
        console.error('Error persisting book:', error);
      }
    },

    async generateCoverImage(originImageId) {
      if (this.isUploading) {
        this.showToast('Please wait for the image to finish uploading', 'warning');
        return;
      }

      try {
        this.isUploading = true;
        const response = await fetch(`/api/images/${originImageId}/cover`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          }
        });

        if (!response.ok) {
          throw new Error('Failed to upload image');
        }

        // Store the image ID in the newBook object
        const result = await response.json();
        this.newBook.imageId = result.id;
        this.showToast('Successfully created cover for image');
      } catch (error) {
        console.error('Error persisting book:', error);
      } finally {
        this.isUploading = false;
      }
    },

    editBook(book) {
        if(!book) {
            return;
        }

        this.selectedBook = { ...book };
        this.newBook = { ...book };
        this.genreInput = book.genre ? book.genre.join(', ') : '';
        this.tagInput = book.tags ? book.tags.join(', ') : '';
        this.drawerMode = 'edit';
        this.drawerOpen = true;
        this.comboboxInputValue = this.newBook?.uniqueId || '';
    },

    editSelectedBook() {
        this.newBook = { ...this.selectedBook };
        this.genreInput = this.selectedBook.genre ? this.selectedBook.genre.join(', ') : '';
        this.tagInput = this.selectedBook.tags ? this.selectedBook.tags.join(', ') : '';
        this.drawerMode = 'edit';
        this.comboboxInputValue = this.newBook?.uniqueId || '';
    },
  
    async handleImageUpload(event, key) {
        const file = event.target.files[0];
        if (!file) return;

        // Create a FormData object to send the file
        const formData = new FormData();
        formData.append('image', file);

        // Show loading state
        this.isUploading = true;

        try {
            // Upload the image
            const response = await fetch('/api/images', {
                method: 'POST',
                body: formData
            });

            if (!response.ok) {
              throw new Error('Failed to upload image');
            }

            // Store the image ID in the newBook object
            const result = await response.json();
            this.newBook[key] = result.id;
            this.showToast('Image uploaded successfully');
        } catch (error) {
            console.error('Error image uploading:', error);
            this.showToast('Failed to upload image: ' + error.message, true);
        } finally {
            this.isUploading = false;
            document.getElementById('imageUpload').value = '';
            document.getElementById('originImageUpload').value = '';
        }
    },

    async importFromRemote() {
        if (!this.remoteUrl) return;

        try {
            const escapedUrl = encodeURIComponent(this.remoteUrl);
            const response = await fetch(`/api/books/remote-import?url=${escapedUrl}`);

            if (response.ok) {
                const data = await response.json();
                this.newBook = data;
                this.showImportModal = false;
            } else {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
        } catch (error) {
             console.error('Error book importing:', error);
            this.showToast('Failed to import book: ' + error.message, true);
        }
    },

    handleScraperResult(event) {
        console.info("event: ", event);
    }

  };
}