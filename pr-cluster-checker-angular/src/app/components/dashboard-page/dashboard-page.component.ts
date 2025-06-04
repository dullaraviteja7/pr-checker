import { Component, OnInit } from '@angular/core';
import { AnalysisResult, AppConfig, ClusterConfig } from '../../models';
import { DashboardService } from '../../services/dashboard.service';
import { ConfigService } from '../../services/config.service'; // To get cluster list for filter

@Component({
  selector: 'app-dashboard-page',
  templateUrl: './dashboard-page.component.html',
  styleUrls: ['./dashboard-page.component.css']
})
export class DashboardPageComponent implements OnInit {

  analysisResult: AnalysisResult | null = null;
  configuredClusters: ClusterConfig[] = [];

  filterDateFrom: string = ''; // Bound to date input
  filterDateTo: string = '';   // Bound to date input
  selectedClusterFilter: string = ''; // Bound to select dropdown

  isLoading: boolean = false;
  errorMessage: string | null = null;

  constructor(
    private dashboardService: DashboardService,
    private configService: ConfigService
  ) { }

  ngOnInit(): void {
    this.loadClusterListForFilter();
    this.loadDashboardData();
  }

  loadClusterListForFilter(): void {
    this.configService.loadConfig().subscribe({
      next: (appConfig) => {
        this.configuredClusters = appConfig.clusters || [];
      },
      error: (err) => {
        console.error('Error loading app configuration for cluster filter', err);
        // Potentially set an error message or handle as needed
      }
    });
  }

  loadDashboardData(): void {
    this.isLoading = true;
    this.errorMessage = null;
    this.analysisResult = null; // Clear previous results

    const dateFrom = this.filterDateFrom ? this.filterDateFrom : undefined;
    const dateTo = this.filterDateTo ? this.filterDateTo : undefined;
    const cluster = this.selectedClusterFilter ? this.selectedClusterFilter : undefined;

    this.dashboardService.getAnalysisResults(dateFrom, dateTo, cluster).subscribe({
      next: (data) => {
        this.analysisResult = data;
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Error loading dashboard data', err);
        this.errorMessage = `Failed to load dashboard data: ${err.message || 'Unknown error'}`;
        this.isLoading = false;
      }
    });
  }

  onFilterChange(): void {
    this.loadDashboardData();
  }

  onClearCache(): void {
    this.isLoading = true; // Show loading indicator while clearing cache and reloading
    this.dashboardService.clearCache().subscribe({
      next: () => {
        alert('Backend cache cleared successfully.');
        this.loadDashboardData(); // Refresh data
      },
      error: (err) => {
        console.error('Error clearing cache', err);
        alert(`Failed to clear cache: ${err.message || 'Unknown error'}`);
        this.isLoading = false; // Ensure loading is stopped on error
      }
    });
  }

  // Helper to iterate over map keys in the template
  getObjectKeys(obj: any): string[] {
    return obj ? Object.keys(obj) : [];
  }
}
