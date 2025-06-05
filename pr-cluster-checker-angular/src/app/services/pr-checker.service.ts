import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PrCheckDetail } from '../models';

@Injectable({
  providedIn: 'root'
})
export class PrCheckerService {
  private apiUrl = 'http://localhost:8080/api/check_pr'; // Backend API URL for PR check

  constructor(private http: HttpClient) { }

  getPrDetails(prNumber: number): Observable<PrCheckDetail> {
    let params = new HttpParams().set('prNumber', prNumber.toString());
    return this.http.get<PrCheckDetail>(this.apiUrl, { params });
  }
}
