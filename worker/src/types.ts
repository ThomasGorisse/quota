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

/** How every client prints a percentage: capacity left, or capacity spent. */
export type DisplayMode = 'remaining' | 'used';

export interface Settings {
  trmnl_webhook_url?: string | null;
  warn: number;
  critical: number;
  display: DisplayMode;
}

export type Severity = 'ok' | 'warn' | 'critical';

export interface Env {
  QUOTA: KVNamespace;
}
