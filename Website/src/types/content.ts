import type { LucideIcon } from 'lucide-react';

/** Module groups */
export type FeatureCategory = 'hud' | 'performance' | 'utility';

export interface FeatureItem {
  id: string;
  name: string;
  category: FeatureCategory;
  description: string;
  benefit: string;
  icon: LucideIcon;
  enabledByDefault: boolean;
  details: string[];
}

export interface CategoryMeta {
  key: FeatureCategory;
  label: string;
  description: string;
  icon: LucideIcon;
}

export interface ChangelogEntry {
  type: 'added' | 'improved' | 'fixed';
  text: string;
}

export interface DownloadBuild {
  id: 'sodium' | 'vulkan';
  version: string;
  channel: 'stable' | 'beta';
  mcVersions: string[];
  loaders: string[];
  releaseDate: string;
  sizeMB: number;
  file: string;
  sha256: string;
  changelog: ChangelogEntry[];
}

export interface RequirementTier {
  name: string;
  badge: string;
  cpu: string;
  gpu: string;
  ram: string;
  java: string;
  storage: string;
  targetFps: string;
}

export interface StatMetric {
  label: string;
  value: number;
  suffix: string;
  decimals?: number;
  description: string;
}

export interface FaqItem {
  question: string;
  answer: string;
}

export interface ComparisonRow {
  capability: string;
  vanilla: string;
  sodium: string;
  valkaw: string;
}

export interface Screenshot {
  src: string;
  title: string;
  caption: string;
  tag: string;
}
