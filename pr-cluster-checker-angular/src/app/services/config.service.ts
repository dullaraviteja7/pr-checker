import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppConfig } from '../models'; // Uses the index.ts for models

@Injectable({
  providedIn: 'root'
})
export class ConfigService {
  private apiUrl = 'http://localhost:8080/api/config'; // Adjust if backend URL differs

  constructor(private http: HttpClient) { }

  loadConfig(): Observable<AppConfig> {
    return this.http.get<AppConfig>(this.apiUrl);
  }

  saveConfig(config: AppConfig): Observable<void> {
    return this.http.post<void>(this.apiUrl, config);
  }
}
