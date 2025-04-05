export function listApp() {
  return {
    books: [],
    searchQuery: '',
    selectedBook: null,
    currentPage: 1,
    pageSize: 10,
    totalItems: 0,
    totalPages: 0,
    isLoading: true,
    errorMessage: '',
    activeId: null,
    activePopupId: null,
    currentRequest: undefined,

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
        this.totalItems = parseInt(response.headers.get('X-Total-Items') || '0');
        this.totalPages = parseInt(response.headers.get('X-Total-Pages') || '0');
        this.currentPage = parseInt(response.headers.get('X-Current-Page') || '1');
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

    searchDebounce() {
        if(this.currentRequest) {
            this.currentRequest.cancel();
            this.currentRequest = undefined;
        }

        this.currentRequest = this.debounce(() => {
            this.search();
        }, 500);

        this.currentRequest();
    },

    search() {
      this.currentPage = 1;
      this.fetchBooks();
    },
  
    prevPage() {
      if (this.currentPage > 1) {
        this.currentPage--;
        this.fetchBooks();
      }
    },
  
    nextPage() {
      if (this.currentPage < this.totalPages) {
        this.currentPage++;
        this.fetchBooks();
      }
    },
  
    goToPage(page) {
      this.currentPage = page;
      this.fetchBooks();
    },
  
    get paginationPages() {
      const rangeSize = 5;
      const pages = [];
  
      let start = Math.max(1, this.currentPage - Math.floor(rangeSize / 2));
      let end = Math.min(this.totalPages, start + rangeSize - 1);
  
      if (end - start + 1 < rangeSize) {
        start = Math.max(1, end - rangeSize + 1);
      }
  
      for (let i = start; i <= end; i++) {
        pages.push(i);
      }
  
      return pages;
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