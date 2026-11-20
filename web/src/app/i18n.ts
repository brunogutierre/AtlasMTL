import { DOCUMENT, Injectable, computed, effect, inject, signal } from '@angular/core';

export type Language = 'en' | 'fr';

const en = {
  tagline: 'Ask about Montréal in plain language and see the answer on a map.',
  nav: { label: 'Pages', ask: 'Ask', stats: 'Stats' },
  stats: {
    title: 'How AtlasMTL is used',
    intro: 'Aggregate numbers over completed answers. No question or visitor is shown.',
    questions: 'Questions answered',
    week: 'Last 7 days',
    cached: 'Replayed from cache',
    cost: 'Average model cost',
    median: 'Median answer time',
    p95: 'Slowest 5% take over',
    toolCalls: 'Tool calls per answer',
    days: 'Questions per day, last 14 days',
    tools: 'Tools used',
    empty: 'No answers yet.',
    failed: 'The statistics couldn’t be loaded.',
  },
  language: 'Language',
  switchTo: 'Français',
  theme: 'Color mode',
  modes: { light: 'Light', dark: 'Dark', system: 'System' },
  chat: {
    title: 'Ask a question',
    label: 'Your question',
    hint: 'Enter to send, Shift+Enter for a new line.',
    placeholder: 'Schools within 1 km of a hospital in Rosemont',
    ask: 'Ask',
    stop: 'Stop',
    conversation: 'Conversation',
    empty: 'Ask about parks, schools, hospitals, CLSCs or care homes anywhere in the Montréal agglomeration.',
    thinking: 'Looking it up…',
    failed: 'The assistant couldn’t answer. Please try again.',
  },
  map: { label: 'Map of the answer' },
  examples: {
    heading: 'Try one of these',
    questions: [
      'Schools within 1 km of a hospital',
      'Parks larger than 2 hectares in Rosemont–La Petite-Patrie',
      'Which borough has the most parks per km²?',
      'Parks within 500 m of 1000 rue Sherbrooke Ouest',
      'CLSCs in Côte-des-Neiges–Notre-Dame-de-Grâce',
    ],
  },
  trace: {
    summary: (steps: number) => `How I found it (${steps} ${steps === 1 ? 'step' : 'steps'})`,
    found: (total: number) => `${total} found`,
    failed: 'failed',
    tools: {
      describe_categories: 'Listed the kinds of places',
      find_features: 'Searched for places',
      within_distance: 'Looked for places near others',
      count_by_borough: 'Counted per borough',
      geocode: 'Located an address',
      get_borough: 'Looked up a borough',
    } as Record<string, string>,
  },
  limits: {
    rate_limited: 'That was quick! Please wait a few seconds before asking again.',
    visitor_budget: 'You have reached today’s question limit. Come back tomorrow.',
    global_budget:
      'Today’s budget for new answers is spent. The example questions still work; new ones will be back tomorrow.',
  } as Record<string, string>,
  results: {
    heading: (count: number) => `On the map (${count})`,
    show: 'Show on the map',
  },
  sources: 'Data',
  attribution:
    'Open data under CC BY 4.0 from the Ville de Montréal, the Ministère de la Santé et des Services sociaux and the Ministère de l’Éducation du Québec.',
};

/** Every language has every message: a missing one is a compile error. */
export type Messages = typeof en;

const fr: Messages = {
  tagline: 'Posez vos questions sur Montréal en langage courant et voyez la réponse sur une carte.',
  nav: { label: 'Pages', ask: 'Demander', stats: 'Statistiques' },
  stats: {
    title: 'Utilisation d’AtlasMTL',
    intro: 'Chiffres agrégés des réponses complètes. Aucune question ni aucun visiteur n’est affiché.',
    questions: 'Questions répondues',
    week: '7 derniers jours',
    cached: 'Rejouées depuis le cache',
    cost: 'Coût moyen du modèle',
    median: 'Temps de réponse médian',
    p95: 'Les 5 % les plus lents dépassent',
    toolCalls: 'Appels d’outils par réponse',
    days: 'Questions par jour, 14 derniers jours',
    tools: 'Outils utilisés',
    empty: 'Aucune réponse pour l’instant.',
    failed: 'Les statistiques n’ont pas pu être chargées.',
  },
  language: 'Langue',
  switchTo: 'English',
  theme: 'Mode de couleur',
  modes: { light: 'Clair', dark: 'Sombre', system: 'Système' },
  chat: {
    title: 'Posez une question',
    label: 'Votre question',
    hint: 'Entrée pour envoyer, Maj+Entrée pour une nouvelle ligne.',
    placeholder: 'Écoles à moins de 1 km d’un hôpital dans Rosemont',
    ask: 'Demander',
    stop: 'Arrêter',
    conversation: 'Conversation',
    empty: 'Posez une question sur les parcs, écoles, hôpitaux, CLSC ou CHSLD de l’agglomération de Montréal.',
    thinking: 'Recherche en cours…',
    failed: 'L’assistant n’a pas pu répondre. Veuillez réessayer.',
  },
  map: { label: 'Carte de la réponse' },
  examples: {
    heading: 'Essayez une de ces questions',
    questions: [
      'Écoles à moins de 1 km d’un hôpital',
      'Parcs de plus de 2 hectares dans Rosemont–La Petite-Patrie',
      'Quel arrondissement compte le plus de parcs par km² ?',
      'Parcs à moins de 500 m du 1000 rue Sherbrooke Ouest',
      'CLSC dans Côte-des-Neiges–Notre-Dame-de-Grâce',
    ],
  },
  trace: {
    summary: (steps: number) => `Comment je l’ai trouvé (${steps} étape${steps === 1 ? '' : 's'})`,
    found: (total: number) => `${total} trouvé${total === 1 ? '' : 's'}`,
    failed: 'échec',
    tools: {
      describe_categories: 'Liste des types de lieux',
      find_features: 'Recherche de lieux',
      within_distance: 'Lieux proches d’autres lieux',
      count_by_borough: 'Décompte par arrondissement',
      geocode: 'Localisation d’une adresse',
      get_borough: 'Recherche d’un arrondissement',
    },
  },
  limits: {
    rate_limited: 'C’était rapide ! Attendez quelques secondes avant de reposer une question.',
    visitor_budget: 'Vous avez atteint la limite de questions pour aujourd’hui. Revenez demain.',
    global_budget:
      'Le budget du jour pour les nouvelles réponses est épuisé. Les exemples fonctionnent encore ; les nouvelles questions reviennent demain.',
  },
  results: {
    heading: (count: number) => `Sur la carte (${count})`,
    show: 'Voir sur la carte',
  },
  sources: 'Données',
  attribution:
    'Données ouvertes sous licence CC BY 4.0 de la Ville de Montréal, du ministère de la Santé et des Services sociaux et du ministère de l’Éducation du Québec.',
};

const MESSAGES: Record<Language, Messages> = { en, fr };
const STORAGE_KEY = 'atlasmtl.language';

/**
 * English and French messages as signals. A few dozen strings don't need a translation library;
 * typed objects catch missing keys at compile time.
 */
@Injectable({ providedIn: 'root' })
export class I18n {
  private readonly document = inject(DOCUMENT);

  readonly language = signal<Language>(this.initial());
  readonly t = computed(() => MESSAGES[this.language()]);

  constructor() {
    effect(() => {
      const language = this.language();
      this.document.documentElement.lang = language;
      try {
        localStorage.setItem(STORAGE_KEY, language);
      } catch {
        // Storage can be unavailable (private mode); the choice then lasts for the visit.
      }
    });
  }

  toggle(): void {
    this.language.update((language) => (language === 'en' ? 'fr' : 'en'));
  }

  private initial(): Language {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored === 'en' || stored === 'fr') {
        return stored;
      }
    } catch {
      // Fall through to the browser language.
    }
    return this.document.defaultView?.navigator.language.startsWith('fr') ? 'fr' : 'en';
  }
}
