export function viewApp() {
  return {

    copyBookMarkdownToClipboard() {
        const selectedBook = this.selectedBook;

        if (!selectedBook) {
          return;
        }

        // Helper function to convert tags to snake_case
        const toSnakeCase = (tag) => {
            return tag
              .toLowerCase()
              .replace(/[^а-яa-z0-9]+/g, '_') // Replace non-alphanumeric characters with underscores
              .replace(/^_+|_+$/g, ''); // Trim leading and trailing underscores
        };

        const formattedTags = selectedBook.tags.map(toSnakeCase).join(', ');

        const markdown = `
        # ${selectedBook.ruName}

        **English Name:** ${selectedBook.enName}

        **Original Name:** ${selectedBook.originName}

        **Author:** ${selectedBook.author}

        **Type:** ${selectedBook.type}

        **Year:** ${selectedBook.year}

        **Chapters:** ${selectedBook.chapters}

        **Genres:** ${selectedBook.genre.join(', ')}

        **Tags:** ${formattedTags}

        **Description:**
        ${selectedBook.description.trim().replace(/\n/g, '\n  ')}

        **Link:** [${selectedBook.linkText}](${selectedBook.link})
        `.replace(/^\s+/gm, '').replace(/$/gm, '  ');

        navigator.clipboard.writeText(markdown).then(() => {
            this.showToast('Markdown copied to clipboard!');
        }).catch(err => {
            this.showToast(`Failed to copy markdown: ${err.message || err.detail}`, true);
            console.error('Failed to copy markdown: ', err);
        });
    }

  };
}