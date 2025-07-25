export function managementApp() {
  return {

    releasesSchedule: [],
    scheduleLoading: true,
    filters: {
        targetId: '',
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

    initManagement() {
        const releaseFilters = localStorage.getItem('releaseFilters');
        if(!!releaseFilters) {
            this.filters = JSON.parse(releaseFilters);
        }
        this.loadState('managementCollapsed');
    },

    async fetchSchedule(bookId) {
        if(!bookId) {
            return;
        }

        this.scheduleLoading = true;
        try {
            const response = await fetch(`/api/books/${bookId}/schedule`);
            if (!response.ok) {
                throw new Error('Failed to fetch releasesSchedule');
            }
            this.releasesSchedule = await response.json();
            this.afterFetchSchedule();
            this.scheduleLoading = false;
        } catch (error) {
            console.error("error: ", error);
            this.showToast('Failed to load schedule: ' + error.message, true);
            this.scheduleLoading = false;
        }
    },
    
    afterFetchSchedule() {
        this.processCharts();
        this.processReleasesForEditing();
    },

    get uniqueTargets() {
        const targets = [];
        return Array.from(this.releasesSchedule
            .map(({ releaseTargetId, releaseActionType }) => ({ releaseTargetId, releaseActionType }))
            .reduce((map, obj) => map.set(obj.releaseTargetId, obj), new Map())
            .values()
        ).sort((a, b) => a.releaseActionType.localeCompare(b.releaseActionType));
    },

    get filteredReleases() {
        let filtered = [...this.releasesSchedule];

        // Filter by target
        if (this.filters.targetId) {
            filtered = filtered.filter(r => r.releaseTargetId === this.filters.targetId);
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

            const nextWeek = new Date(today);
            nextWeek.setDate(today.getDate() + 7);

            const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);
            const monthEnd = new Date(today)
            monthEnd.setDate(monthStart.getDate() + 30);

            // Filter by status
            if (this.filters.date === 'today') {
                filtered = filtered.filter(r => new Date(r.date) == today);
            } else if (this.filters.date === 'thisWeek') {
                filtered = filtered.filter(r => new Date(r.date) >= weekStart && new Date(r.date) <= weekEnd);
            } else if (this.filters.date === 'nextWeek') {
                filtered = filtered.filter(r => new Date(r.date) >= today && new Date(r.date) <= nextWeek);
            } else if (this.filters.date === 'nextMonth') {
                filtered = filtered.filter(r => new Date(r.date) >= monthStart && new Date(r.date) <= monthEnd);
            }
        }

        // Sort
        if (this.filters.sortBy === 'date') {
            filtered.sort((a, b) => new Date(a.date) - new Date(b.date));
        } else if (this.filters.sortBy === 'target') {
            filtered.sort((a, b) => a.releaseActionType.localeCompare(b.releaseActionType));
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
     async markAsExecuted(release) {
        if(!release) return;

        release.executed = !release.executed;
        release.status = 'EXECUTED';

        try {
          const response = await fetch(`/api/releases/${release.id}/executed`, {
            method: 'PATCH',
            headers: {
              'Content-Type': 'application/json'
            }
          });

          if (response.status === 204) {
            this.showToast('Flag updated successfully');
          } else if (response.status === 404) {
            this.showToast('Release not found', false);
          } else {
            this.showToast('Unexpected response:' + response.status, true);
          }
        } catch (error) {
          console.error('Request failed:', error);
          this.showToast('Error on changing execution flag: ' + error.message, true);
        }

        this.processCharts();
    },

    // Mark release as pending
    async markAsPending(release) {
        // In a real app, you would call an API to update the release
        release.executed = false;
        release.status = 'DRAFT';

        try {
            const response = await fetch(`/api/releases/${release.id}/executed`, {
              method: 'PATCH',
              headers: {
                'Content-Type': 'application/json',
              },
              body: JSON.stringify({})
            });

            if (response.status === 204) {
                this.showToast('Flag updated successfully');
                this.processCharts();
            } else {
                throw new Error('Failed to mark as pending');
            }
        } catch (error) {
          console.error('Error action execution:', error);
          this.showToast('Failed to execute action: ' + error.message, true);
        }
    },

    // Bulk execute releases
    bulkExecuteReleases() {
        this.filteredReleases.forEach(release => {
            release.executed = true;
            release.status = 'DRAFT';
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

        this.showToast('Book execution is scheduled!');
      } catch (error) {
        console.error('Error schedule execution:', error);
        this.showToast('Error schedule execution: ' + error.message, true);
      }
    },

    daysDiff(inputDate) {
      const today = new Date(new Date().toISOString().split('T')[0]);
      const input = new Date(inputDate);
      return Math.abs(Math.ceil((input - today) / (1000 * 60 * 60 * 24)));
    },

    getStatusClass(release) {
        const classes = {
            'DRAFT': 'bg-yellow-100 text-yellow-800',
            'MANUAL_ACTION_REQUIRED': 'bg-red-100 text-red-800',
            'EXECUTED': 'bg-green-100 text-green-800'
        };

        return classes[release.status] || 'bg-gray-100 text-gray-800';
    }

  };
}