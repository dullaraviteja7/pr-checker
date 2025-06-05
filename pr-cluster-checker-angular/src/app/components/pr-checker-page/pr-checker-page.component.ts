import { Component, OnInit } from '@angular/core';
import { PrCheckDetail, AppConfig, ClusterConfig } from '../../models';
import { PrCheckerService } from '../../services/pr-checker.service';
import { ConfigService } from '../../services/config.service';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-pr-checker-page',
  templateUrl: './pr-checker-page.component.html',
  styleUrls: ['./pr-checker-page.component.css']
})
export class PrCheckerPageComponent implements OnInit {

  prNumberInput: string = '';
  prDetails: PrCheckDetail | null = null;
  isLoading: boolean = false;
  errorMessage: string | null = null;
  appConfig: AppConfig | null = null;

  constructor(
    private prCheckerService: PrCheckerService,
    private configService: ConfigService
  ) { }

  ngOnInit(): void {
    this.loadAppConfig();
  }

  loadAppConfig(): void {
    this.configService.loadConfig().subscribe({
      next: (config) => {
        this.appConfig = config;
      },
      error: (err) => {
        console.error('Error loading AppConfig', err);
        this.errorMessage = 'Could not load application configuration. Some features might be limited.';
      }
    });
  }

  onCheckPr(): void {
    if (!this.prNumberInput || isNaN(Number(this.prNumberInput))) {
      this.errorMessage = 'Please enter a valid PR number.';
      this.prDetails = null;
      return;
    }

    const prNumber = Number(this.prNumberInput);
    this.isLoading = true;
    this.errorMessage = null;
    this.prDetails = null;

    this.prCheckerService.getPrDetails(prNumber).subscribe({
      next: (data) => {
        this.prDetails = data;
        this.isLoading = false;
      },
      error: (err: HttpErrorResponse) => {
        console.error('Error fetching PR details', err);
        if (err.status === 404) {
          this.errorMessage = `PR #${prNumber} not found.`;
        } else if (err.error && typeof err.error === 'string' && err.error.includes("Git repository URL is not configured")) {
          this.errorMessage = 'Application is not configured. Please set the Git Repository URL in the Configuration page.';
        }
         else {
          this.errorMessage = `Failed to fetch PR details: ${err.statusText || 'Unknown error'}`;
        }
        this.isLoading = false;
      }
    });
  }

  // Helper to iterate over map keys in the template
  getObjectKeys(obj: any): string[] {
    return obj ? Object.keys(obj) : [];
  }
}
