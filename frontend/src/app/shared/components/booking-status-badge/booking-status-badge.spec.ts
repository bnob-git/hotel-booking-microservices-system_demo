import { TestBed } from '@angular/core/testing';
import { BookingStatusBadge } from './booking-status-badge';

describe('BookingStatusBadge', () => {
  it('renders a friendly label and status class', () => {
    const fixture = TestBed.createComponent(BookingStatusBadge);
    fixture.componentRef.setInput('value', 'PARTIALLY_REFUNDED');
    fixture.detectChanges();

    const span: HTMLElement = fixture.nativeElement.querySelector('span');
    expect(span.textContent?.trim()).toBe('Partially refunded');
    expect(span.classList).toContain('badge--partially_refunded');
    expect(span.getAttribute('data-status')).toBe('PARTIALLY_REFUNDED');
  });

  it('falls back to a dash without a value', () => {
    const fixture = TestBed.createComponent(BookingStatusBadge);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent.trim()).toBe('—');
  });
});
