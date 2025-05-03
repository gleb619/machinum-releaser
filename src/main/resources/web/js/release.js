export function releaseApp() {
  return {

    releases: [],
    isReleaseFormOpen: false,
    currentRelease: {
        date: new Date().toISOString().split('T')[0],
        chapters: 1,
        executed: false,
        metadata: {}
    },
    currentTarget: {
        name: '',
        metadata: {}
    },
    targetReleaseId: null,
    currentSchedule: {
        releaseTargetName: '',
        startDate: new Date().toISOString().split('T')[0],
        dayThreshold: 4,
        amountOfChapters: 0,
        startBulk: 0.1,
        endBulk: 0.1,
        minChapters: 10,
        maxChapters: 50,
        peakWidth: 0.4,
        smoothFactor: 0.2,
        randomFactor: 0.3,
        periodCount: 12.0
    },

    releaseBook(book) {
        if(!book) {
            return;
        }

        this.selectedBook = book;
        this.drawerMode = 'releases';
        this.drawerOpen = true;
        this.fetchReleases(book.id);
    },

    fetchReleases(bookId) {
        fetch(`/api/books/${bookId}/releases`)
            .then(response => response.json())
            .then(data => {
                this.releases = data;
            })
            .catch(error => {
                this.showToast('Error loading releases: ' + error.message, true);
            });
    },

    afterReleasesChanged() {
        this.processCharts();
        this.processReleasesForEditing();
    },

    openReleaseCreateForm() {
        if(this.currentSchedule.amountOfChapters <= 1) {
            this.currentSchedule.amountOfChapters = this.releases.length > 0 ? this.releases[0].chaptersCount : 0;
        }
        this.isReleaseFormOpen = true;
    },

    editRelease(release) {
        this.currentRelease = { ...release };
        this.isReleaseFormOpen = true;
    },

    deleteRelease(releaseId) {
        if (confirm('Are you sure you want to delete this release?')) {
            fetch(`/api/releases/${releaseId}`, {
                method: 'DELETE'
            })
                .then(response => {
                    if (response.ok) {
                        this.showToast('Release deleted successfully');
                        this.fetchReleases(this.selectedBook.id);
                        this.afterReleasesChanged();
                    } else {
                        throw new Error('Failed to delete release');
                    }
                })
                .catch(error => {
                    this.showToast('Error: ' + error.message, true);
                });
        }
    },

    saveRelease() {
        const isUpdate = this.currentRelease.id !== undefined;
        const url = isUpdate ? `/api/releases/${this.currentRelease.id}` : '/api/releases';
        const method = isUpdate ? 'PUT' : 'POST';

        // Add the book ID if creating a new release
        if (!isUpdate) {
            this.currentRelease.bookId = this.selectedBook.id;
        }

        fetch(url, {
            method: method,
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(this.currentRelease)
        })
            .then(response => {
                if (response.ok) {
                    return response.json();
                } else {
                    throw new Error('Failed to save release');
                }
            })
            .then(data => {
                this.showToast(`Release ${isUpdate ? 'updated' : 'created'} successfully`);
                this.isReleaseFormOpen = false;
                this.fetchReleases(this.selectedBook.id);
                this.afterReleasesChanged();
            })
            .catch(error => {
                this.showToast('Error: ' + error.message, true);
            });
    },

    formatDate(dateString) {
        const options = { year: 'numeric', month: 'long', day: 'numeric' };
        return new Date(dateString).toLocaleDateString(undefined, options);
    },

    getTargetIcon(targetName) {
        const iconMap = {
            'Telegram': '/image/telegram-icon.jpg',
            'Website': '/image/website-icon.png',
            'App': '/image/app-icon.png',
            'Discord': '/image/discord-icon.png',
            'Email': '/image/email-icon.png'
        };

        return iconMap[targetName] || '/image/default-icon.png';
    },

    generateSchedule() {
        fetch(`/api/books/${this.selectedBook.id}/generate-release`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(this.currentSchedule)
        })
            .then(data => {
                if(data.ok || data.created) {
                    this.showToast(`Generated release for ${this.currentSchedule.name} successfully`);
                    this.isReleaseFormOpen = false;
                    this.fetchReleases(this.selectedBook.id);
                    this.afterReleasesChanged();
                } else {
                    this.showToast('Error: ' + data.message, true);
                }
            })
            .catch(error => {
                console.error("error: ", error);
                this.showToast('Error: ' + error.message, true);
            });
    }

  };
}