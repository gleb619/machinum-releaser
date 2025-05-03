export function chartApp() {
  return {
    targetCharts: [],
    pivotData: [],
    pivotTotals: {
        releases: 0,
        chapters: 0,
        executed: 0,
        executionRate: 0
    },

    processCharts() {
        this.targetCharts.splice(0, this.targetCharts.length);
        this.pivotData.splice(0, this.pivotData.length);
        this.pivotTotals = {
            releases: 0,
            chapters: 0,
            executed: 0,
            executionRate: 0
        };
        this.processChartReleases();
        this.createPivotTable();
    },

    processChartReleases() {
        // Group releasesSchedule by target name
        const targets = {};
        this.releasesSchedule.forEach(release => {
            if (!targets[release.releaseTargetName]) {
                targets[release.releaseTargetName] = [];
            }
            targets[release.releaseTargetName].push(release);
        });

        // Create charts for each target
        let chartIndex = 0;
        for (const [targetName, releasesSchedule] of Object.entries(targets)) {
            const canvasId = `chart-${chartIndex}`;
            this.targetCharts.push({
                targetName,
                canvasId,
                releasesSchedule
            });
            chartIndex++;
        }

        // Render charts after DOM update
        this.$nextTick(() => {
            this.cleanCharts();
            this.renderCharts();
            this.renderCombinedChart();
        });
    },

    cleanCharts() {
        this.targetCharts.forEach(targetChart => {
            const canvas = document.getElementById(targetChart.canvasId);
            if (canvas) {
                const chart = Chart.getChart(canvas);
                if (chart) {
                    chart.clear();
                    chart.destroy();
                }
            }
        });

        const combinedCanvas = document.getElementById('combinedChart');
        if (combinedCanvas) {
            const chart = Chart.getChart(combinedCanvas);
            if (chart) {
                chart.clear();
                chart.destroy();
            }
        }
    },

    renderCharts() {
        this.targetCharts.forEach(chart => {
            const canvas = document.getElementById(chart.canvasId);
            if (!canvas) return;

            const ctx = canvas.getContext('2d');

            // Sort releasesSchedule by date
            const sortedReleases = [...chart.releasesSchedule].sort((a, b) =>
                new Date(a.date) - new Date(b.date));

            // Prepare data
            const labels = sortedReleases.map(r => r.date);
            const plannedData = sortedReleases.map(r => r.chapters);
            const executedData = sortedReleases.map(r => r.executed ? r.chapters : 0);

            new Chart(ctx, {
                type: 'bar',
                data: {
                    labels: labels,
                    datasets: [
                        {
                            label: 'Planned Chapters',
                            data: plannedData,
                            backgroundColor: 'rgba(54, 162, 235, 0.5)',
                            borderColor: 'rgba(54, 162, 235, 1)',
                            borderWidth: 1
                        },
                        {
                            label: 'Executed Chapters',
                            data: executedData,
                            backgroundColor: 'rgba(75, 192, 192, 0.5)',
                            borderColor: 'rgba(75, 192, 192, 1)',
                            borderWidth: 1
                        }
                    ]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: {
                        y: {
                            beginAtZero: true,
                            title: {
                                display: true,
                                text: 'Number of Chapters'
                            }
                        },
                        x: {
                            title: {
                                display: true,
                                text: 'Release Date'
                            }
                        }
                    }
                }
            });
        });
    },

    renderCombinedChart() {
        const canvas = document.getElementById('combinedChart');
        if (!canvas) return;

        const ctx = canvas.getContext('2d');

        // Prepare datasets for each target
        const datasets = [];
        const colors = [
            'rgba(54, 162, 235, 0.7)',
            'rgba(75, 192, 192, 0.7)',
            'rgba(255, 159, 64, 0.7)',
            'rgba(153, 102, 255, 0.7)',
            'rgba(255, 99, 132, 0.7)'
        ];

        // Get all unique dates
        const allDates = new Set();
        this.releasesSchedule.forEach(release => {
            allDates.add(release.date);
        });
        const sortedDates = [...allDates].sort((a, b) => new Date(a) - new Date(b));

        // Create datasets for each target
        this.targetCharts.forEach((chart, index) => {
            const color = colors[index % colors.length];

            // Create a map of date to chapters for this target
            const dateToChapters = {};
            chart.releasesSchedule.forEach(release => {
                dateToChapters[release.date] = release.chapters;
            });

            // Create data array with values for all dates
            const data = sortedDates.map(date => dateToChapters[date] || 0);

            datasets.push({
                label: chart.targetName,
                data: data,
                backgroundColor: color,
                borderColor: color.replace('0.7', '1'),
                borderWidth: 1
            });
        });

        new Chart(ctx, {
            type: 'line',
            data: {
                labels: sortedDates,
                datasets: datasets
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    y: {
                        beginAtZero: true,
                        title: {
                            display: true,
                            text: 'Number of Chapters'
                        }
                    },
                    x: {
                        title: {
                            display: true,
                            text: 'Release Date'
                        }
                    }
                }
            }
        });
    },

    downloadChart(canvasId) {
        const canvas = document.getElementById(canvasId);

        html2canvas(canvas.parentNode).then(canvas => {
            // Create anchor element
            const link = document.createElement('a');
            link.download = `${canvasId}-${new Date().toISOString()}.png`;
            link.href = canvas.toDataURL('image/png');
            link.click();
        });
    },

    createPivotTable() {
        this.pivotData = [];
        let totalReleases = 0;
        let totalChapters = 0;
        let totalExecutedChapters = 0;

        // Process each target
        this.targetCharts.forEach(chart => {
            const releases = chart.releases;
            const targetName = chart.targetName;

            // Calculate metrics
            const chartReleases = chart.releasesSchedule.length;
            const chartChapters = chart.releasesSchedule.reduce((sum, r) => sum + r.chapters, 0);
            const executedChapters = chart.releasesSchedule
                .filter(r => r.executed)
                .reduce((sum, r) => sum + r.chapters, 0);
            const executionRate = chartChapters > 0 ? (executedChapters / chartChapters) : 0;

            // Find latest release date
            const latestRelease = chart.releasesSchedule.length > 0
                ? new Date(Math.max(...chart.releasesSchedule.map(r => new Date(r.date))))
                : null;

            const nextRelease = chart.releasesSchedule.length > 0
                ? new Date(Math.min(...chart.releasesSchedule.filter(r => !r.executed).map(r => new Date(r.date))))
                : null;

            this.pivotData.push({
                targetName,
                chartReleases,
                chartChapters,
                executedChapters,
                executionRate,
                latestRelease: latestRelease ? latestRelease.toISOString().split('T')[0] : '-',
                nextRelease: nextRelease ? nextRelease.toISOString().split('T')[0] : '-'
            });

            // Add to totals
            totalReleases += chartReleases;
            totalChapters += chartChapters;
            totalExecutedChapters += executedChapters;
        });

        // Calculate total execution rate
        const overallExecutionRate = totalChapters > 0
            ? (totalExecutedChapters / totalChapters)
            : 0;

        this.pivotTotals = {
            releases: totalReleases,
            chapters: totalChapters,
            executed: totalExecutedChapters,
            executionRate: overallExecutionRate
        };
    },

    formatPercentage(value) {
        return `${(value * 100).toFixed(1)}%`;
    },

    getExecutionRateClass(rate) {
        if (rate >= 0.9) return 'text-green-600 font-medium';
        if (rate >= 0.7) return 'text-yellow-600 font-medium';
        return 'text-red-600 font-medium';
    },

    getDateClass(inputDate, executed = false) {
        if(executed) {
            return "";
        }

        const today = new Date(new Date().toISOString().split('T')[0]);
        const input = new Date(inputDate);
        const diffDays = Math.ceil((input - today) / (1000 * 60 * 60 * 24));

        if (diffDays < 0) return 'text-red-600';
        if (diffDays <= 1) return 'text-red-500';
        if (diffDays <= 2) return 'text-red-400';
        if (diffDays <= 7) return 'text-yellow-500';
        if (diffDays <= 14) return 'text-purple-500';
        if (diffDays <= 21) return 'text-blue-500';
        return 'text-green-600';
    },

  };
}