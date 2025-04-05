export function initApp() {
  return {

    init() {
      this.fetchBooks();
      this.initManagement();
    },

  };
}