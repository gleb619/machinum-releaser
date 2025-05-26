export function listApp() {
  return {
    books: [],
    searchQuery: '',
    selectedBook: null,
    currentPage: 0,
    pageSize: 10,
    totalElements: 0,
    totalPages: 0,
    isLoading: true,
    errorMessage: '',
    activeId: null,
    activePopupId: null,
    currentRequest: undefined,

    initList() {
      this.fetchBooks();
    },

    setSelectedItem(bookId) {
      this.activeId = bookId;
      this.selectedBook = this.books.find(item => item.id === this.activePopupId);
    },

    openDrawer() {
      this.setSelectedItem(this.activePopupId);
      this.drawerMode = 'view';
      this.drawerOpen = true;
    },
  
    async fetchBooks() {
      this.isLoading = true;
      try {
        const url = new URL('/api/books', window.location.origin);
        url.searchParams.append('page', this.currentPage);
        url.searchParams.append('size', this.pageSize);
  
        if (this.searchQuery.trim()) {
          url.searchParams.append('query', this.searchQuery.trim());
        }
  
        const response = await fetch(url);
  
        if (!response.ok) {
          throw new Error('Failed to fetch books');
        }
  
        // Get pagination headers
        this.totalElements = parseInt(response.headers.get('X-Total-Items') || '0');
        this.totalPages = parseInt(response.headers.get('X-Total-Pages') || '0');
        this.currentPage = parseInt(response.headers.get('X-Current-Page') || '0');
        this.pageSize = parseInt(response.headers.get('X-Page-Size') || '10');
  
        const data = await response.json();
        this.books = data;
        this.afterFetch();
      } catch (error) {
        this.errorMessage = error.message;
        console.error('Error fetching books:', error);
        this.showToast('Failed to fetch books: ' + error.message, true);
      } finally {
        this.isLoading = false;
      }
    },

    afterFetch() {
        if(this.books && this.books.length > 0) {
            const itemId = this.activeId || this.books[0].id;
            this.setSelectedItem(itemId);
            this.fetchSchedule(itemId);
        } else {
            this.activeId = undefined;
        }
    },

    search() {
      this.currentPage = 0;
      this.fetchBooks();
    },
  
    goToPage(page) {
      this.currentPage = page;
      this.fetchBooks();
    },
  
    // Add these methods
    toggleDropdown(id) {
        this.activePopupId = this.activePopupId === id ? null : id;
    },

    releaseActiveBook() {
        this.releaseBook(this.books.find(item => item.id === this.activeId));
    },

    editActiveBook() {
        this.editBook(this.books.find(item => item.id === this.activeId));
    },

    deleteActiveBook() {
        this.deleteBook(this.books.find(item => item.id === this.activeId));
    },

    deleteBook(book) {
        if(!book) {
            return;
        }

        if (confirm(`Are you sure you want to delete ${book.ruName}?`)) {
            // Implement your delete logic here
            fetch(`/api/books/${book.id}`, {
                method: 'DELETE',
                headers: {
                    'Content-Type': 'application/json',
                }
            })
            .then(response => {
                if (response.ok) {
                    this.showToast('Book deleted successfully');
                    this.fetchBooks(); // Refresh the book list
                } else {
                    throw new Error('Failed to delete book');
                }
            })
            .catch(error => {
                this.showToast('Error: ' + error.message, 'error');
            });
        }
        this.activeId = null;
    },

  };
}