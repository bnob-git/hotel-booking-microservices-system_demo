import { Routes } from '@angular/router';

import { Home } from './pages/home/home';
import { Bookings } from './pages/bookings/bookings';
import { Login } from './pages/login/login';

import { bookingGuard } from './core/guards/booking.guard';
import { adminGuard } from './core/guards/admin.guard';
import { ChatComponent } from './pages/chat/chat';

export const routes: Routes = [
  { path: '', component: Home },

  {
    path: 'bookings',
    component: Bookings,
    canActivate: [bookingGuard],
  },

  {
    path: 'admin',
    loadComponent: () => import('./pages/admin/admin').then((m) => m.Admin),
    canActivate: [adminGuard],
  },

  {
    path: 'chat',
    component: ChatComponent,
    canActivate: [adminGuard],
  },
  { path: 'login', component: Login },

  // Track D: guest checkout (no login required)
  {
    path: 'guest/book',
    loadComponent: () => import('./pages/guest-booking/guest-booking').then((m) => m.GuestBooking),
  },
  {
    path: 'guest/lookup',
    loadComponent: () => import('./pages/guest-lookup/guest-lookup').then((m) => m.GuestLookup),
  },

  { path: '**', redirectTo: '' },
];
