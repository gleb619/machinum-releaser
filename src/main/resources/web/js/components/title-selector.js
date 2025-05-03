export function comboboxApp() {
  return {
    comboboxOpen: false,
    comboboxInputValue: '',
    comboboxOptions: [],
    filteredComboboxOptions: [],
    selectedComboboxOption: null,
    highlightedComboboxIndex: -1,

    async initCombobox() {
        // Fetch initial data
        try {
            const response = await fetch('/api/books/titles', {
                headers: {
                    'Accept': 'application/json',
                },
                cache: 'default'
            });

            if (!response.ok) throw new Error('Failed to fetch');

            const tempResult = await response.json();
            this.comboboxOptions = Object.keys(tempResult).map(key => tempResult[key]);
            this.filteredComboboxOptions = [...this.comboboxOptions];
        } catch (error) {
            console.error('Error fetching data:', error);
            if(this.showToast) {
                this.showToast('Failed to load book titles: ' + error.message, true);
            }
        }
    },

    filterComboboxOptions() {
        if (!this.comboboxInputValue) {
            this.filteredComboboxOptions = [...this.comboboxOptions];
            return;
        }

        const searchTerm = this.comboboxInputValue.toLowerCase();
        this.filteredComboboxOptions = this.comboboxOptions.filter(option =>
            option.toLowerCase().includes(searchTerm)
        );

        this.highlightedComboboxIndex = this.filteredComboboxOptions.length > 0 ? 0 : -1;
    },

    toggleComboboxDropdown() {
        this.comboboxOpen = !this.comboboxOpen;
        if (this.comboboxOpen) this.filterComboboxOptions();
    },

    selectComboboxOption(option) {
        this.selectedComboboxOption = option;
        this.comboboxInputValue = option;
        this.comboboxOpen = false;
        this.setNewUniqueId();
    },

    highlightComboboxNext() {
        if (!this.comboboxOpen) {
            this.comboboxOpen = true;
            return;
        }

        if (this.highlightedComboboxIndex < this.filteredComboboxOptions.length - 1) {
            this.highlightedComboboxIndex++;
        }
    },

    highlightComboboxPrev() {
        if (this.highlightedComboboxIndex > 0) {
            this.highlightedComboboxIndex--;
        }
    },

    selectComboboxHighlighted() {
        if (this.highlightedComboboxIndex >= 0 && this.highlightedComboboxIndex < this.filteredComboboxOptions.length) {
            this.selectComboboxOption(this.filteredComboboxOptions[this.highlightedComboboxIndex]);
        } else if (this.filteredComboboxOptions.length === 0 && this.comboboxInputValue) {
            // Allow custom entry
            this.selectedComboboxOption = this.comboboxInputValue;
            this.comboboxOpen = false;
        }
    },

    setNewUniqueId() {
        this.newBook.uniqueId = this.comboboxInputValue;
    }

  }
}