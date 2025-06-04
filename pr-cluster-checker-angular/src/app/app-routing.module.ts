import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ConfigPageComponent } from './components/config-page/config-page.component';
import { DashboardPageComponent } from './components/dashboard-page/dashboard-page.component';
import { PrCheckerPageComponent } from './components/pr-checker-page/pr-checker-page.component';

const routes: Routes = [
  { path: 'config', component: ConfigPageComponent },
  { path: 'dashboard', component: DashboardPageComponent },
  { path: 'check-pr', component: PrCheckerPageComponent },
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' }, // Default to dashboard
  // { path: '**', redirectTo: '/dashboard' } // Wildcard route
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
