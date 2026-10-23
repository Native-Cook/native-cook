import { DELIVERY, EXPRESS_FEE, ORDER_ID, SUBTOTAL } from './beam-theme';

export type FormError = { field: number; message: string };
export type FormModel = {
  values: string[]; step: number; furthest: number; express: boolean;
  error: FormError | null; paid: boolean; newOrder: boolean; restarting: boolean;
};
export type FormAction =
  | { type: 'edit'; field: number; value: string }
  | { type: 'focus'; field: number }
  | { type: 'continue' }
  | { type: 'go'; target: number }
  | { type: 'delivery'; express: boolean }
  | { type: 'settled'; target: number }
  | { type: 'cleared' | 'restarted' | 'newOrder' | 'paid' };
export type FormCommand = { type: string; a?: number; b?: number };

/** Creates a fresh checkout without retaining payment information. */
export function emptyForm(): FormModel {
  return { values: ['', '', '', '', '', '', ''], step: 0, furthest: 0, express: false, error: null, paid: false, newOrder: false, restarting: false };
}
/** Formats one field while preserving natural partial input. */
export function formatField(field: number, value: string): string {
  if (field === 0) return value.trim();
  if (field < 3) return value;
  const digits = value.replace(/\D/g, '');
  if (field === 3) return digits.slice(0, 5);
  if (field === 4) return digits.slice(0, 16).replace(/(.{4})(?=.)/g, '$1 ');
  if (field === 5) {
    const date = digits.slice(0, 4);
    return date.length >= 2 ? `${date.slice(0, 2)}/${date.slice(2)}` : date;
  }
  return digits.slice(0, 4);
}
/** Checks the checksum of a complete sixteen-digit card number. */
export function luhn(value: string): boolean {
  const digits = value.replace(/\D/g, '');
  if (digits.length !== 16 || /^0+$/.test(digits)) return false;
  let sum = 0;
  for (let i = 0; i < digits.length; i++) {
    let digit = Number(digits[i]);
    if (i % 2 === 0) { digit *= 2; if (digit > 9) digit -= 9; }
    sum += digit;
  }
  return sum % 10 === 0;
}
/** Returns the first invalid field; the current month remains valid through its last day. */
export function validateStep(step: number, values: readonly string[], now = new Date()): FormError | null {
  if (step === 0 && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values[0].trim())) return { field: 0, message: 'Enter a valid email' };
  if (step === 1) {
    if (values[1].trim().length < 4) return { field: 1, message: 'Enter your street address' };
    if (values[2].trim().length < 2) return { field: 2, message: 'Enter your city' };
    if (!/^\d{5}$/.test(values[3])) return { field: 3, message: 'Enter a 5-digit ZIP' };
  }
  if (step === 2) {
    if (!luhn(values[4])) return { field: 4, message: 'Check the card number' };
    const match = /^(\d{2})\/(\d{2})$/.exec(values[5]);
    const month = match ? Number(match[1]) : 0;
    const year = match ? 2000 + Number(match[2]) : 0;
    if (month < 1 || month > 12 || year * 12 + month < now.getFullYear() * 12 + now.getMonth() + 1) return { field: 5, message: 'Check the expiry date' };
    if (!/^\d{3,4}$/.test(values[6])) return { field: 6, message: 'Enter the 3-digit code' };
  }
  return null;
}
/** Calculates the complete order total in dollars. */
export function orderTotal(express: boolean): number { return SUBTOTAL + (express ? EXPRESS_FEE : 0); }
/** Formats the order total with a stable two-decimal currency label. */
export function totalLabel(express: boolean): string { return `$${orderTotal(express).toFixed(2)}`; }
/** Returns the condensed values displayed on completed rows. */
export function rowSummaries(model: FormModel): string[] {
  return [model.values[0], `${model.values[2]} · ${model.express ? 'Express' : 'Standard'}`, `•••• ${model.values[4].replace(/\D/g, '').slice(-4)}`, totalLabel(model.express), ORDER_ID];
}
/** Returns the four review lines using the current form values. */
export function reviewLines(model: FormModel): [string, string][] {
  return [['Contact', model.values[0]], ['Ship to', `${model.values[1]}, ${model.values[2]}`], ['Delivery', DELIVERY[model.express ? 1 : 0].summary], ['Card', rowSummaries(model)[2]]];
}
/** Chooses the visible CTA copy from the interactive step and payment state. */
export function ctaLabel(model: FormModel): string {
  if (model.step < 3 || model.restarting) return 'Continue';
  if (model.step === 3) return `${model.paid ? 'Paid' : 'Pay'} ${totalLabel(model.express)}`;
  return model.newOrder ? 'New order' : `Paid ${totalLabel(model.express)}`;
}
/** Applies the same form actions for real interactions and the headless demonstration. */
export function formAction(model: FormModel, action: FormAction, now = new Date()): { model: FormModel; commands: FormCommand[] } {
  let next = { ...model };
  const commands: FormCommand[] = [];
  if (action.type === 'edit') {
    next.values = [...model.values];
    next.values[action.field] = formatField(action.field, action.value);
    commands.push({ type: 'filled', a: action.field, b: next.values[action.field].length > 0 ? 1 : 0 });
    if (model.error?.field === action.field) { next.error = null; commands.push({ type: 'clearError' }); }
  } else if (action.type === 'focus') commands.push({ type: 'focus', a: action.field });
  else if (action.type === 'delivery') {
    next.express = action.express;
    commands.push({ type: 'delivery', a: action.express ? 1 : 0 });
  } else if (action.type === 'continue') {
    if (model.step === 3 && model.paid) return { model, commands };
    if (model.step === 4) { if (model.restarting) return { model, commands }; next.restarting = true; commands.push({ type: 'restart' }); }
    else {
      const error = validateStep(model.step, model.values, now);
      next.error = error;
      if (error) commands.push({ type: 'reject', a: error.field });
      else if (model.step === 3) commands.push({ type: 'pay' });
      else if (model.step < model.furthest) commands.push({ type: 'go', a: model.furthest });
      else commands.push({ type: 'advance' });
    }
  } else if (action.type === 'go' && !model.paid && action.target <= model.furthest) commands.push({ type: 'go', a: action.target });
  else if (action.type === 'settled') {
    next.step = action.target; next.furthest = Math.max(model.furthest, action.target); next.error = null; next.paid = action.target === 4;
  } else if (action.type === 'cleared' || action.type === 'restarted') next = emptyForm();
  else if (action.type === 'newOrder') next.newOrder = true;
  else if (action.type === 'paid') next.paid = true;
  return { model: next, commands };
}
