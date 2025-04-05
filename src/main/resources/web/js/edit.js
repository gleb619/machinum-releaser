export function editApp() {
  return {
    drawerOpen: false,
    drawerMode: 'view',
    isUploading: false,
    genreInput: '',
    tagInput: '',
    newBook: {
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
      imageId: null,
    },
  
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
        imageId: null,
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
          throw new Error('Failed to persist book');
        }
  
        await this.fetchBooks();
        this.drawerOpen = false;
        this.showToast('Book changed successfully!');
      } catch (error) {
        console.error('Error persisting book:', error);
        this.showToast('Failed to persist book: ' + error.message, true);
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
    },

    editSelectedBook() {
        this.newBook = { ...this.selectedBook };
        this.genreInput = this.selectedBook.genre ? this.selectedBook.genre.join(', ') : '';
        this.tagInput = this.selectedBook.tags ? this.selectedBook.tags.join(', ') : '';
        this.drawerMode = 'edit';
    },
  
    async handleImageUpload(event) {
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
            this.newBook.imageId = result.id;
            this.showToast('Image uploaded successfully');
        } catch (error) {
            console.error('Error image uploading:', error);
            this.showToast('Failed to upload image: ' + error.message, true);
        } finally {
            this.isLoading = false;
            this.isUploading = false;
            document.getElementById('imageUpload').value = '';
        }
    },

  };
}