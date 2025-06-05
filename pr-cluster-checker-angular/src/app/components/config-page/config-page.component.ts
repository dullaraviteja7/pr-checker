import { Component, OnInit } from '@angular/core';
import { AppConfig, ClusterConfig, GitAuthConfig } from '../../models';
import { ConfigService } from '../../services/config.service';

@Component({
  selector: 'app-config-page',
  templateUrl: './config-page.component.html',
  styleUrls: ['./config-page.component.css']
})
export class ConfigPageComponent implements OnInit {

  config: AppConfig = {
    gitRepoUrl: null,
    authConfig: { method: null, username: null },
    mainReleaseBranch: null,
    clusters: [],
    dateRangeFrom: null,
    dateRangeTo: null
  };

  // To hold temporary string for date inputs, as ngModel with date pipe can be tricky
  dateRangeFromString: string | null = null;
  dateRangeToString: string | null = null;


  constructor(private configService: ConfigService) { }

  ngOnInit(): void {
    this.loadConfig();
  }

  loadConfig(): void {
    this.configService.loadConfig().subscribe({
      next: (data) => {
        this.config = data;
        // Initialize authConfig if it's null from backend
        if (!this.config.authConfig) {
            this.config.authConfig = { method: null, username: null };
        }
        // Ensure clusters is an array
        if (!this.config.clusters) {
            this.config.clusters = [];
        }
        this.dateRangeFromString = this.config.dateRangeFrom;
        this.dateRangeToString = this.config.dateRangeTo;
      },
      error: (err) => {
        console.error('Error loading configuration', err);
        alert('Failed to load configuration.');
         // Initialize with defaults if load fails, to prevent runtime errors in template
        this.config = {
            gitRepoUrl: '',
            authConfig: { method: null, username: '' },
            mainReleaseBranch: '',
            clusters: [],
            dateRangeFrom: '',
            dateRangeTo: ''
        };
        this.dateRangeFromString = '';
        this.dateRangeToString = '';
      }
    });
  }

  saveConfiguration(): void {
    // Update config object with potentially modified date strings
    this.config.dateRangeFrom = this.dateRangeFromString;
    this.config.dateRangeTo = this.dateRangeToString;

    this.configService.saveConfig(this.config).subscribe({
      next: () => {
        alert('Configuration saved successfully!');
      },
      error: (err) => {
        console.error('Error saving configuration', err);
        alert('Failed to save configuration.');
      }
    });
  }

  addCluster(): void {
    if (!this.config.clusters) {
      this.config.clusters = [];
    }
    this.config.clusters.push({ name: '', releaseBranch: '' });
  }

  removeCluster(index: number): void {
    if (this.config.clusters && this.config.clusters.length > index) {
      this.config.clusters.splice(index, 1);
    }
  }

  trackByFn(index: any, item: any): any {
    return index;
  }
}
