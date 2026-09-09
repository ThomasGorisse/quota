/** Shared shapes for the Quota API contract (v1). See ../../README.md. */

export interface Window {
  id: string;
  label: string;
  percent: number;
  resets_at: string | null;
}

export interface Provider {
  plan: string | null;
  windows: Window[];
  extra?: Record<string, unknown>;
}

export interface Snapshot {
  collected_at: string;
  providers: Record<string, Provider>;
}

export interface HistoryPoint {
  t: string;
  [series: string]: string | number;
}

export interface Settings {
  trmnl_webhook_url?: string | null;
  warn: number;
  critical: number;
}

export type Severity = 'ok' | 'warn' | 'critical';

export interface Env {
  QUOTA: KVNamespace;
}
