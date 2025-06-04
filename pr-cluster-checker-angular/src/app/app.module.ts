import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http'; // Added
import { FormsModule } from '@angular/forms'; // Added

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { ConfigPageComponent } from './components/config-page/config-page.component';
import { DashboardPageComponent } from './components/dashboard-page/dashboard-page.component';
import { PrCheckerPageComponent } from './components/pr-checker-page/pr-checker-page.component';

@NgModule({
  declarations: [
    AppComponent,
    ConfigPageComponent,
    DashboardPageComponent,
    PrCheckerPageComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    HttpClientModule, // Added
    FormsModule        // Added
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
