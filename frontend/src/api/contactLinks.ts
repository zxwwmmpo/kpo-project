import type { Contact } from './account';

export const contactLabels = {
  PHONE: 'Телефон',
  URL: 'URL',
  GITHUB: 'GitHub',
};

export function webLink(value: string) {
  try {
    const url = new URL(value);
    return ['https:', 'http:'].includes(url.protocol) ? url.href : undefined;
  } catch {
    return undefined;
  }
}

export function contactLink(contact: Contact) {
  if (contact.type !== 'PHONE') return webLink(contact.value);
  return /^\+[1-9][0-9]{7,14}$/.test(contact.value)
    ? 'tel:' + contact.value
    : undefined;
}
