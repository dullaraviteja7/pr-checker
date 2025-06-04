import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AnalysisResult } from '../models';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private apiUrl = 'http://localhost:8080/api'; // Base API URL

  constructor(private http: HttpClient) { }

  getAnalysisResults(dateFrom?: string, dateTo?: string, filterClusterName?: string): Observable<AnalysisResult> {
    let params = new HttpParams();
    if (dateFrom) {
      params = params.append('dateFrom', dateFrom);
    }
    if (dateTo) {
      params = params.append('dateTo', dateTo);
    }
    if (filterClusterName && filterClusterName.trim() !== '') {
      params = params.append('filterClusterName', filterClusterName);
    }
    return this.http.get<AnalysisResult>(`${this.apiUrl}/dashboard`, { params });
  }

  clearCache(): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/clear_cache`, {});
  }
}
