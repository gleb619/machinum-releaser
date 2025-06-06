let previewChart = null;

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
        periodCount: 12.0,
        metadata: {}
    },
    previewReleases: [],

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
        alert("Doesn't work right now")
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

    generateSchedule(preview = false) {
        if(!previewChart) {
            this.createPreviewChart();
        }

        fetch(`/api/books/${this.selectedBook.id}/generate-release?preview=${preview}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(this.currentSchedule)
        })
            .then(response => {
                if(preview) {
                    response.json()
                        .then(data => {
                            if(response.status == 202) {
                                this.previewReleases = data;
                                this.previewUpdateChart();
                            } else {
                                this.showToast('Error: ' + data.message, true);
                            }
                        });
                } else {
                    if(response.ok || response.created) {
                        this.showToast(`Generated release for ${this.currentSchedule.name} successfully`);
                        this.isReleaseFormOpen = false;
                        this.fetchReleases(this.selectedBook.id);
                        this.afterReleasesChanged();
                    } else {
                        this.showToast('Error: ' + response.message, true);
                    }
                }
            })
            .catch(error => {
                console.error("error: ", error);
                this.showToast('Error: ' + error.message, true);
            });
    },

    createPreviewChart() {
        const el = document.getElementById('previewReleaseChart');
        if(!el) {
            console.error("Element 'previewReleaseChart' not found");
            return;
        }
        const ctx = el.getContext('2d');
        previewChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: ['Data'],
                datasets: [{
                    label: 'now',
                    data: [0],
                    borderColor: 'rgb(59, 130, 246)',
                    backgroundColor: 'rgba(59, 130, 246, 0.1)',
                    tension: 0.4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    y: {
                        beginAtZero: true
                    }
                }
            }
        });
    },

    get previewTotalChapters() {
        return this.previewReleases.reduce((sum, release) => sum + (release.chapters || 0), 0);
    },

    get releaseDuration() {
        const validDates = this.previewReleases
            .map(r => r.date)
            .filter(date => date && date.trim() !== '')
            .map(date => new Date(date))
            .filter(date => !isNaN(date.getTime()));

        if (validDates.length === 0) return 0;

        const startDate = new Date(Math.min(...validDates));
        const endDate = new Date(Math.max(...validDates));

        return Math.ceil((endDate - startDate) / (1000 * 60 * 60 * 24));
    },

     get averageChapters() {
        if (this.previewReleases.length === 0) return '0.0';
        const avg = this.previewTotalChapters / this.previewReleases.length;
        return avg.toFixed(1);
    },

    previewUpdateChart() {
        this.$nextTick(() => {
            const sortedReleases = [...this.previewReleases]
                .filter(r => r.date)
                .sort((a, b) => new Date(a.date) - new Date(b.date));

            previewChart.data.labels = sortedReleases.map(r =>
                new Date(r.date).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
            );
            previewChart.data.datasets[0].data = sortedReleases.map(r => r.chapters || 0);

            previewChart.update();
        });
    },

    previewAddRelease() {
        const newRelease = {
            id: Date.now().toString(),
            releaseTargetName: '',
            releaseTargetId: '',
            date: '',
            chapters: 1,
            executed: false,
            metadata: {},
            showMetadata: false,
            newMetadataKey: '',
            newMetadataValue: ''
        };
        this.previewReleases.push(newRelease);
        this.previewUpdateChart();
    },

    previewRemoveRelease(index) {
        this.previewReleases.splice(index, 1);
        this.previewUpdateChart();
    },

    previewDuplicateRelease(index) {
        const original = this.previewReleases[index];
        const duplicate = {
            ...original,
            id: Date.now().toString(),
            executed: false,
            showMetadata: false,
            newMetadataKey: '',
            newMetadataValue: ''
        };
        this.previewReleases.splice(index + 1, 0, duplicate);
        this.previewUpdateChart();
    },

    previewUpdateRelease(index) {
        // Trigger reactivity and update chart
        this.$nextTick(() => {
            this.previewUpdateChart();
        });
    },

    previewAddMetadata(index) {
        const release = this.previewReleases[index];
        if (release.newMetadataKey && release.newMetadataValue) {
            release.metadata[release.newMetadataKey] = release.newMetadataValue;
            release.newMetadataKey = '';
            release.newMetadataValue = '';
        }
    },

    previewRemoveMetadata(releaseIndex, key) {
        delete this.previewReleases[releaseIndex].metadata[key];
    },

  };
}