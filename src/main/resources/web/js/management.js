export function managementApp() {
  return {

    releasesSchedule: [],
    loading: true,
    filters: {
        targetName: '',
        status: '',
        date: '',
        sortBy: 'date'
    },
    showConfirmModal: false,
    confirmModal: {
        title: '',
        message: '',
        onConfirm: () => {},
        onCancel: () => {}
    },
    managementCollapsed: false,

    async fetchSchedule(bookId) {
        if(!bookId) {
            return;
        }

        this.loading = true;
        try {
            const response = await fetch(`/api/books/${bookId}/schedule`);
            if (!response.ok) {
                throw new Error('Failed to fetch releasesSchedule');
            }
            this.releasesSchedule = await response.json();
            this.afterFetchSchedule();
            this.loading = false;
        } catch (error) {
            console.error("error: ", error);
            this.showToast('Failed to load schedule: ' + error.message, true);
            this.loading = false;
        }
    },
    
    afterFetchSchedule() {
        this.processCharts();
        this.processReleasesForEditing();
    },

    get uniqueTargets() {
        const targets = new Set();
        this.releasesSchedule.forEach(release => {
            targets.add(release.releaseTargetName);
        });
        return [...targets].sort();
    },

    get filteredReleases() {
        let filtered = [...this.releasesSchedule];

        // Filter by target
        if (this.filters.targetName) {
            filtered = filtered.filter(r => r.releaseTargetName === this.filters.targetName);
        }

        // Filter by status
        if (this.filters.status === 'executed') {
            filtered = filtered.filter(r => r.executed);
        } else if (this.filters.status === 'pending') {
            filtered = filtered.filter(r => !r.executed);
        }

        if(this.filters.date != '') {
            const now = new Date();
            const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
            const monday = now.getDate() - now.getDay() + (now.getDay() == 0 ? -6 : 1); // adjust when day is sunday
            const sunday = monday + 6;

            const weekStart = new Date(today);
            const weekEnd = new Date(today);
            weekStart.setDate(monday);
            weekEnd.setDate(sunday);

            const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);
            const monthEnd = new Date(today)
            monthEnd.setDate(monthStart.getDate() + 30);

            // Filter by status
            if (this.filters.date === 'today') {
                filtered = filtered.filter(r => new Date(r.date) == today);
            } else if (this.filters.date === 'thisWeek') {
                filtered = filtered.filter(r => new Date(r.date) >= weekStart && new Date(r.date) <= weekEnd);
            } else if (this.filters.date === 'nextMonth') {
                filtered = filtered.filter(r => new Date(r.date) >= monthStart && new Date(r.date) <= monthEnd);
            }
        }

        // Sort
        if (this.filters.sortBy === 'date') {
            filtered.sort((a, b) => new Date(a.date) - new Date(b.date));
        } else if (this.filters.sortBy === 'target') {
            filtered.sort((a, b) => a.releaseTargetName.localeCompare(b.releaseTargetName));
        } else if (this.filters.sortBy === 'chapters') {
            filtered.sort((a, b) => b.chapters - a.chapters);
        }

        return filtered;
    },

    // Process releases to add editing properties
    processReleasesForEditing() {
        this.releasesSchedule.forEach(release => {
            release.editing = false;
            release.editData = {
                date: release.date,
                chapters: release.chapters
            };
        });
    },

    // Edit release
    editRelease(release) {
        release.editing = true;
        release.editData = {
            date: release.date,
            chapters: release.chapters
        };
    },

    // Save edited release
    saveRelease(release) {
        // In a real app, you would call an API to update the release
        release.date = release.editData.date;
        release.chapters = release.editData.chapters;
        release.editing = false;

        this.processCharts();
    },

    // Cancel edit
    cancelEdit(release) {
        release.editing = false;
    },

    // Mark release as executed
    markAsExecuted(release) {
        // In a real app, you would call an API to update the release
        release.executed = true;

        this.processCharts();
    },

    // Mark release as pending
    markAsPending(release) {
        // In a real app, you would call an API to update the release
        release.executed = false;

        this.processCharts();
    },

    // Bulk execute releases
    bulkExecuteReleases() {
        this.filteredReleases.forEach(release => {
            release.executed = true;
        });

        //TODO Update on backend
    },

    // Move releases to next month
    moveReleasesToNextPoint() {
        const pendingReleases = this.filteredReleases.filter(r => !r.executed);
        const diff = this.currentSchedule.dayThreshold || 4;

        pendingReleases.forEach(release => {
            let date = new Date(release.date);
            date.setDate(date.getDate() + diff);
            release.date = date.toISOString().split('T')[0];
        });

        //TODO Update on backend
    },

    persistFilters() {
        localStorage.setItem('releaseFilters', JSON.stringify(this.filters));
    },

    async executeRelease(release) {
      try {
        const response = await fetch(`/api/releases/${release.id}/execute`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({})
        });

        if (!response.ok) {
          throw new Error('Failed to execute release');
        }

        this.executed = true;
        this.showToast('Book execution is scheduled!');
      } catch (error) {
        console.error('Error schedule execution:', error);
        this.showToast('Error schedule execution: ' + error.message, true);
      }
    },

    // Modify your init function to include the editing properties
    initManagement() {
        const releaseFilters = localStorage.getItem('releaseFilters');
        if(!!releaseFilters) {
            this.filters = JSON.parse(releaseFilters);
        }
        this.loadState('managementCollapsed');
    }

  };
}