import { Component, DestroyRef, OnInit, computed, effect, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

type Stav = 'NOVA' | 'V_POSUDZOVANI' | 'ROZHODNUTE';

interface Scope { nazov: string; vsetko: boolean; mozeUpravovat: boolean; }
interface Me { username: string; name: string; email: string; groups: string[]; scope: Scope; loginTime: string; }
interface Predpis { rok: string; cislo: string; paragraf: string; nazov: string; }
interface Ziadost {
  id: string; ziadatel: string; sluzba: string; obec: string; poskytovatel: string; stav: Stav;
  podana: string; termin: string; popis: string; predpis: Predpis; tagy: string[];
}

interface Odpoved<T> { sluzba: string; url: string; data: T | null; xml: string | null; chyba: string | null; }
interface Obdobie { zaciatok: string; koniec: string | null; }
interface Preukaz { cislo: string; typ: string; platnyOd: string; platnyDo: string | null; }
interface Tzp { evidovany: boolean; aktualne: boolean; obdobia: Obdobie[]; preukazy: Preukaz[]; parkovaciePreukazy: Preukaz[]; }
interface StavObdobie { zaciatok: string; koniec: string | null; stav: string; }
interface PpnoZaznam {
  poberatel: string; ziadostId: string; urad: string; okres: string; podana: string;
  opatrovanaOd: string; opatrovanaDo: string | null; rola: string; aktivny: boolean; obdobia: StavObdobie[];
}
interface Ppno { poskytuje: boolean; zaznamy: PpnoZaznam[]; }
interface Sluzba {
  id: string; druh: string; forma: string; miesto: string | null; kapacita: number | null; od: string | null;
  koniec: string | null; cieloveSkupiny: string[]; zodpovedna: string; email: string | null; telefon: string | null;
  zapis: string; vymaz: string | null; aktivna: boolean;
}
interface Rss { najdeny: boolean; nazov: string; ico: string; typ: string; adresa: string; statutar: string; sluzby: Sluzba[]; }
interface Csru { ziadost: string; rcMaskovane: string; ico: string; tzp: Odpoved<Tzp>; ppno: Odpoved<Ppno>; rss: Odpoved<Rss>; zdroj: string; }
interface Zmena { register: string; datum: string; typ: string; osoba: string; cas: string; }

const STAV_TEXT: Record<Stav, string> = { NOVA: 'Nová', V_POSUDZOVANI: 'V posudzovaní', ROZHODNUTE: 'Rozhodnutá' };

@Component({
  selector: 'app-root',
  imports: [FormsModule, DatePipe],
  template: `
    <a href="#main" class="govuk-skip-link">Preskočiť na hlavný obsah</a>

    <!-- ===================== HLAVIČKA (ID-SK 3.0, rovnaká ako vo fóre) ===================== -->
    <div class="govuk-header__wrapper">
      <header class="govuk-header idsk-shadow-head">
        <div class="govuk-header__container">
          <div class="idsk-secondary-navigation govuk-width-container">
            <div class="idsk-secondary-navigation__header">
              <div class="idsk-secondary-navigation__heading">
                <div class="idsk-secondary-navigation__heading-title">
                  <span class="idsk-secondary-navigation__heading-mobile">SK</span>
                  <span class="idsk-secondary-navigation__heading-desktop">Oficiálna stránka</span>
                  <button type="button" class="govuk-button govuk-button--texted--inverse idsk-secondary-navigation__heading-button"
                          [attr.aria-expanded]="govOpen()" (click)="govOpen.set(!govOpen())">
                    <span class="idsk-secondary-navigation__heading-mobile">e-Gov</span>
                    <span class="idsk-secondary-navigation__heading-desktop"><b>verejnej správy SR</b></span>
                    <span class="material-icons" aria-hidden="true">{{ govOpen() ? 'arrow_drop_up' : 'arrow_drop_down' }}</span>
                  </button>
                </div>
                <div class="idsk-secondary-navigation__body" [class.hidden]="!govOpen()">
                  <div class="idsk-secondary-navigation__text">
                    <div>
                      <h3 class="govuk-body-s"><b>Doména gov.sk je oficiálna</b></h3>
                      <p class="govuk-body-s">Toto je oficiálna webová stránka orgánu verejnej moci Slovenskej republiky. Oficiálne stránky využívajú najmä doménu gov.sk.</p>
                    </div>
                    <div>
                      <h3 class="govuk-body-s"><b>Táto stránka je zabezpečená</b></h3>
                      <p class="govuk-body-s">Buďte pozorní a vždy sa uistite, že zdieľate informácie iba cez zabezpečenú webovú stránku verejnej správy SR.</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="govuk-predheader govuk-width-container">
          <div class="govuk-header__logo">
            <a href="/" class="govuk-header__link govuk-header__link--homepage sos-logo-text" title="Agenda sociálnych služieb – úvod">
              <img src="assets/agenda-logo.svg" alt="Agenda sociálnych služieb – úvodná stránka">
            </a>
          </div>
          <div class="govuk-header__btns-search">
            @if (me()) {
              <button type="button" class="govuk-button govuk-button--texted sos-portal-menu-btn" aria-controls="navigation"
                      [attr.aria-expanded]="menuOpen()" (click)="menuOpen.set(!menuOpen())">
                <span class="material-icons" aria-hidden="true">{{ menuOpen() ? 'close' : 'menu' }}</span> Menu
              </button>
            }
            <div class="govuk-header__actionPanel desktop sos-user-panel">
              <a class="govuk-button govuk-button--sec sos-portal-btn" [href]="forumHomeUrl()">
                <span class="material-icons" aria-hidden="true">forum</span> Komunitné fórum
              </a>
              @if (me(); as m) {
                <span class="sos-portal-user">
                  <span class="sos-avatar" aria-hidden="true">{{ initials(m) }}</span>
                  <span class="sos-portal-user__text">
                    <b>{{ m.name || m.username }}</b>
                    <span class="govuk-body-s sos-muted">{{ m.scope.nazov }}</span>
                  </span>
                </span>
                <a class="govuk-button govuk-button--texted govuk-button--texted__warning sos-portal-logout" href="/logout">Odhlásiť sa</a>
              } @else if (!loading()) {
                <a class="govuk-button govuk-button__basic sos-signin-btn" href="/oauth2/authorization/keycloak">Prihlásiť sa</a>
              }
            </div>
          </div>
        </div>

        @if (me()) {
          <nav id="navigation" aria-label="Hlavná navigácia" class="govuk-header__navigation govuk-width-container" [class.sos-portal-nav--open]="menuOpen()">
            <div class="govuk-header__navigation-list">
              <ul>
                <li class="govuk-header__navigation-item govuk-header__navigation-item--active" aria-current="page">
                  <a class="govuk-header__link" href="/">Žiadosti</a>
                </li>
                @if (me()!.scope.vsetko) {
                  <li class="govuk-header__navigation-item"><a class="govuk-header__link" href="#zmeny">Zmeny v registroch</a></li>
                }
                <li class="govuk-header__navigation-item"><a class="govuk-header__link" [href]="forumHomeUrl()">Komunitné fórum</a></li>
              </ul>
            </div>
          </nav>
        }
      </header>
    </div>

    <div class="govuk-width-container">
      <div class="govuk-notification-banner sos-demo-banner" role="region" aria-labelledby="demo-banner-title">
        <div class="govuk-notification-banner__header">
          <h2 class="govuk-notification-banner__title" id="demo-banner-title">Ukážka</h2>
        </div>
        <div class="govuk-notification-banner__content">
          <p class="govuk-body sos-mb-0">Demo aplikácia Platformy SOS – všetky žiadosti, osoby a údaje z registrov sú <b>ilustračné</b>.</p>
        </div>
      </div>

      <main class="govuk-main-wrapper sos-main" id="main" tabindex="-1">
        @if (loading()) {
          <p class="govuk-body" role="status">Načítavam…</p>
        } @else if (!me()) {
          <!-- ===================== NEPRIHLÁSENÝ ===================== -->
          @if (loggedOut()) {
            <div class="govuk-notification-banner govuk-notification-banner--error" role="alert" aria-labelledby="logout-title">
              <div class="govuk-notification-banner__header">
                <h2 class="govuk-notification-banner__title" id="logout-title">Boli ste odhlásený</h2>
              </div>
              <div class="govuk-notification-banner__content">
                <p class="govuk-notification-banner__heading">Odhlásenie prebehlo v inej aplikácii Platformy SOS (napr. vo fóre).</p>
                <p class="govuk-body sos-mb-0">Jednotné odhlásenie (single logout) cez Keycloak. Pre pokračovanie sa prihláste znova.</p>
              </div>
            </div>
          }
          <div class="govuk-grid-row">
            <div class="govuk-grid-column-two-thirds-from-desktop">
              <span class="govuk-caption-l">Platforma SOS</span>
              <h1 class="govuk-heading-xl">Posudzovanie odkázanosti na sociálnu službu</h1>
              <p class="govuk-body-l">Ukážková agenda pre pracovníkov obcí, poskytovateľov a rezortu. Prihlásenie je spoločné
                s komunitným fórom Platformy SOS – stačí sa prihlásiť raz.</p>
              <a class="govuk-button govuk-button__basic sos-start-btn" href="/oauth2/authorization/keycloak">
                Prihlásiť sa cez Keycloak <span class="material-icons" aria-hidden="true">arrow_forward</span>
              </a>
            </div>
          </div>

          <h2 class="govuk-heading-l">Ako to funguje</h2>
          <ol class="sos-portal-steps">
            <li class="idsk-card">
              <div class="idsk-card__content">
                <div class="idsk-card__heading"><h3 class="govuk-heading-s">1. Prihláste sa</h3></div>
                <div class="idsk-card__description">Účty <code>obec.test</code>, <code>poskytovatel.test</code> alebo <code>rezort.test</code>
                  (heslo <code>test</code>). Každý vidí iné žiadosti podľa skupiny v Keycloaku.</div>
              </div>
            </li>
            <li class="idsk-card">
              <div class="idsk-card__content">
                <div class="idsk-card__heading"><h3 class="govuk-heading-s">2. Vyberte žiadosť</h3></div>
                <div class="idsk-card__description">Pri každej je právny predpis, stav a referenčné údaje z IS CSRÚ. Obec a rezort môžu meniť stav.</div>
              </div>
            </li>
            <li class="idsk-card">
              <div class="idsk-card__content">
                <div class="idsk-card__heading"><h3 class="govuk-heading-s">3. Opýtajte sa v komunite</h3></div>
                <div class="idsk-card__description">Tlačidlo otvorí fórum s predvyplnenou otázkou – bez ďalšieho prihlasovania (SSO).</div>
              </div>
            </li>
          </ol>
        } @else {
          <!-- ===================== PRIHLÁSENÝ ===================== -->
          <div class="sos-page-head">
            <div>
              <span class="govuk-caption-l">{{ me()!.scope.nazov }}</span>
              <h1 class="govuk-heading-xl sos-portal-h1">Žiadosti o posúdenie odkázanosti</h1>
            </div>
          </div>

          <p class="govuk-body sos-portal-sso">
            <span class="material-icons" aria-hidden="true">verified_user</span>
            Prihlásený cez Keycloak ako <b>{{ me()!.username }}</b> · skupiny:
            @for (g of me()!.groups; track g) { <span class="sos-tag">{{ g }}</span> } @empty { <i>žiadne</i> }
            <span class="sos-muted">· rovnaká identita platí aj vo fóre</span>
          </p>

          <ul class="sos-portal-stats" aria-label="Prehľad žiadostí">
            <li class="sos-portal-stat sos-portal-stat--nova"><span class="sos-portal-stat__n">{{ count('NOVA') }}</span> Nové</li>
            <li class="sos-portal-stat sos-portal-stat--posud"><span class="sos-portal-stat__n">{{ count('V_POSUDZOVANI') }}</span> V posudzovaní</li>
            <li class="sos-portal-stat sos-portal-stat--rozh"><span class="sos-portal-stat__n">{{ count('ROZHODNUTE') }}</span> Rozhodnuté</li>
            <li class="sos-portal-stat sos-portal-stat--late"><span class="sos-portal-stat__n">{{ urgent() }}</span> Termín do 14 dní</li>
          </ul>

          <div class="govuk-grid-row">
            <!-- zoznam -->
            <div class="govuk-grid-column-one-half-from-desktop">
              @if (cases().length === 0) {
                <h2 class="govuk-heading-m">Žiadosti</h2>
                <p class="sos-empty">Pre vašu skupinu nie sú dostupné žiadne žiadosti.</p>
              } @else {
                <table class="govuk-table sos-portal-table">
                  <caption class="govuk-table__caption govuk-heading-m">Žiadosti ({{ cases().length }})</caption>
                  <thead class="govuk-table__head">
                    <tr class="govuk-table__row">
                      <th scope="col" class="govuk-table__header">Číslo</th>
                      <th scope="col" class="govuk-table__header">Služba / obec</th>
                      <th scope="col" class="govuk-table__header">Stav</th>
                      <th scope="col" class="govuk-table__header">Termín</th>
                    </tr>
                  </thead>
                  <tbody class="govuk-table__body">
                    @for (z of cases(); track z.id) {
                      <tr class="govuk-table__row sos-portal-row" [class.sos-portal-row--sel]="z.id === selectedId()" (click)="selectedId.set(z.id)">
                        <th scope="row" class="govuk-table__header">
                          <a class="govuk-link" [href]="'#' + z.id" [attr.aria-current]="z.id === selectedId() ? 'true' : null"
                             (click)="$event.preventDefault(); selectedId.set(z.id)">{{ z.id }}</a>
                        </th>
                        <td class="govuk-table__cell"><div>{{ z.sluzba }}<span class="sos-portal-sub">{{ z.obec }}</span></div></td>
                        <td class="govuk-table__cell"><span class="sos-status" [class]="statusClass(z.stav)">{{ stavText(z.stav) }}</span></td>
                        <td class="govuk-table__cell sos-nowrap" [class.sos-portal-late]="isUrgent(z)">
                          {{ z.termin | date:'d. M. yyyy' }}
                          @if (isUrgent(z)) { <span class="govuk-visually-hidden">(blíži sa termín)</span><span class="material-icons sos-portal-late__ico" aria-hidden="true">schedule</span> }
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              }
            </div>

            <!-- detail -->
            <div class="govuk-grid-column-one-half-from-desktop">
              @if (selected(); as z) {
                <section class="sos-portal-detail" aria-labelledby="detail-title">
                  <span class="govuk-caption-m">Detail žiadosti</span>
                  <h2 class="govuk-heading-l" id="detail-title">{{ z.id }}</h2>

                  <dl class="sos-portal-facts">
                    <dt>Žiadateľ</dt><dd>{{ z.ziadatel }}</dd>
                    <dt>Služba</dt><dd>{{ z.sluzba }}</dd>
                    <dt>Obec</dt><dd>{{ z.obec }}</dd>
                    <dt>Poskytovateľ</dt><dd>{{ z.poskytovatel }}</dd>
                    <dt>Podaná</dt><dd>{{ z.podana | date:'d. M. yyyy' }}</dd>
                    <dt>Termín</dt><dd>{{ z.termin | date:'d. M. yyyy' }}</dd>
                    <dt>Stav</dt><dd><span class="sos-status" [class]="statusClass(z.stav)">{{ stavText(z.stav) }}</span></dd>
                  </dl>
                  <p class="govuk-body">{{ z.popis }}</p>

                  <div class="sos-portal-law">
                    <p class="govuk-body sos-mb-0"><b>{{ z.predpis.paragraf }}</b> {{ z.predpis.nazov }}</p>
                    <a class="govuk-link" [href]="slovLexUrl(z)" target="_blank" rel="noopener">Otvoriť na Slov-Lex<span class="govuk-visually-hidden"> (otvorí sa v novom okne)</span></a>
                  </div>

                  <!-- referenčné údaje -->
                  <h3 class="govuk-heading-m">Referenčné údaje z IS CSRÚ <span class="sos-status sos-status--warning">mock</span></h3>
                  @if (csruLoading()) {
                    <p class="govuk-body sos-muted" role="status">Načítavam údaje z registrov…</p>
                  } @else if (csruError()) {
                    <p class="govuk-body sos-portal-err">{{ csruError() }}</p>
                  } @else {
                    @if (csru(); as c) {
                      <p class="govuk-body-s sos-muted">Dopyt podľa RČ žiadateľa <code>{{ c.rcMaskovane }}</code> a IČO poskytovateľa <code>{{ c.ico }}</code> · {{ c.zdroj }}</p>
                      <div class="sos-portal-reg">
                        <div class="sos-portal-reg__card">
                          <h4 class="govuk-heading-s sos-mb-0">Evidencia ŤZP</h4>
                          <p class="govuk-body-s sos-muted">MPSVaR · IS RSD</p>
                          @if (c.tzp.chyba) { <p class="govuk-body sos-portal-err">{{ c.tzp.chyba }}</p> }
                          @else { @if (c.tzp.data; as t) {
                            @if (t.evidovany) {
                              <span class="sos-status" [class.sos-status--success]="t.aktualne" [class.sos-status--neutral]="!t.aktualne">{{ t.aktualne ? 'Evidovaný ako ŤZP' : 'V minulosti ŤZP' }}</span>
                              <ul class="govuk-list govuk-list--bullet sos-portal-reg__list">
                                @for (o of t.obdobia; track o.zaciatok) { <li>ŤZP od {{ o.zaciatok }}{{ o.koniec ? ' do ' + o.koniec : ' (trvá)' }}</li> }
                                @for (p of t.preukazy; track p.cislo) { <li>Preukaz {{ p.typ }} č. {{ p.cislo }} od {{ p.platnyOd | date:'d. M. yyyy' }}{{ p.platnyDo ? ' do ' + (p.platnyDo | date:'d. M. yyyy') : '' }}</li> }
                                @for (p of t.parkovaciePreukazy; track p.cislo) { <li>Parkovací preukaz č. {{ p.cislo }}</li> }
                                @if (t.preukazy.length === 0) { <li class="sos-muted">preukaz zatiaľ nevydaný</li> }
                              </ul>
                            } @else {
                              <span class="sos-status sos-status--neutral">Nie je v evidencii</span>
                            }
                          } }
                        </div>
                        <div class="sos-portal-reg__card">
                          <h4 class="govuk-heading-s sos-mb-0">Peňažný príspevok na opatrovanie</h4>
                          <p class="govuk-body-s sos-muted">neformálni opatrovatelia · IS RSD</p>
                          @if (c.ppno.chyba) { <p class="govuk-body sos-portal-err">{{ c.ppno.chyba }}</p> }
                          @else { @if (c.ppno.data; as p) {
                            @if (p.zaznamy.length === 0) {
                              <span class="sos-status sos-status--neutral">Žiadny opatrovateľ s PPnO</span>
                            } @else {
                              <span class="sos-status" [class.sos-status--success]="p.poskytuje" [class.sos-status--neutral]="!p.poskytuje">{{ p.poskytuje ? 'PPnO sa poskytuje' : 'PPnO ukončený' }}</span>
                              <ul class="govuk-list sos-portal-reg__list">
                                @for (op of p.zaznamy; track op.ziadostId) {
                                  <li>Opatrovateľ <b>{{ op.poberatel }}</b> – opatrovanie od {{ op.opatrovanaOd | date:'d. M. yyyy' }}{{ op.opatrovanaDo ? ' do ' + (op.opatrovanaDo | date:'d. M. yyyy') : '' }}
                                    <br><span class="govuk-body-s sos-muted">žiadosť {{ op.ziadostId }}, úrad {{ op.urad }} · stav: {{ op.obdobia[op.obdobia.length - 1].stav }}</span></li>
                                }
                              </ul>
                            }
                          } }
                        </div>
                        <div class="sos-portal-reg__card sos-portal-reg__card--wide">
                          <h4 class="govuk-heading-s sos-mb-0">Register sociálnych služieb</h4>
                          <p class="govuk-body-s sos-muted">MPSVaR · IS SOS</p>
                          @if (c.rss.chyba) { <p class="govuk-body sos-portal-err">{{ c.rss.chyba }}</p> }
                          @else { @if (c.rss.data; as r) {
                            @if (!r.najdeny) { <span class="sos-status sos-status--neutral">Poskytovateľ nie je v registri</span> }
                            @else {
                              <p class="govuk-body sos-mb-0"><b>{{ r.nazov }}</b> · IČO {{ r.ico }}</p>
                              <p class="govuk-body-s sos-muted">{{ r.typ }} · {{ r.adresa }} · {{ r.statutar }}</p>
                              <table class="govuk-table sos-portal-table sos-portal-table--compact">
                                <caption class="govuk-table__caption govuk-visually-hidden">Registrované sociálne služby poskytovateľa</caption>
                                <thead class="govuk-table__head">
                                  <tr class="govuk-table__row">
                                    <th scope="col" class="govuk-table__header">Služba</th>
                                    <th scope="col" class="govuk-table__header">Forma</th>
                                    <th scope="col" class="govuk-table__header">Kapacita</th>
                                    <th scope="col" class="govuk-table__header">Stav</th>
                                  </tr>
                                </thead>
                                <tbody class="govuk-table__body">
                                  @for (s of r.sluzby; track s.id) {
                                    <tr class="govuk-table__row" [class.sos-portal-off]="!s.aktivna">
                                      <td class="govuk-table__cell"><div>{{ s.druh }}<span class="sos-portal-sub">{{ s.miesto }}</span></div></td>
                                      <td class="govuk-table__cell"><div><span class="sos-portal-mlabel">Forma: </span>{{ s.forma }}</div></td>
                                      <td class="govuk-table__cell"><div><span class="sos-portal-mlabel">Kapacita: </span>{{ s.kapacita ?? '–' }}</div></td>
                                      <td class="govuk-table__cell"><div>
                                        <span class="sos-status" [class.sos-status--success]="s.aktivna" [class.sos-status--neutral]="!s.aktivna">
                                          {{ s.aktivna ? 'poskytuje sa' : (s.vymaz ? 'vymazaná' : 'neposkytuje sa') }}</span>
                                        @if (s.vymaz) { <span class="sos-portal-sub">{{ s.vymaz | date:'d. M. yyyy' }}</span> }
                                      </div></td>
                                    </tr>
                                  }
                                </tbody>
                              </table>
                            }
                          } }
                        </div>
                      </div>
                      <details class="govuk-details">
                        <summary class="govuk-details__summary">
                          <span class="govuk-details__summary-text">Zobraziť XML odpovede (štruktúra podľa XSD z integračného manuálu)</span>
                        </summary>
                        <div class="govuk-details__text">
                          @for (o of [c.tzp, c.ppno, c.rss]; track o.url) {
                            <p class="govuk-body-s"><b>{{ o.sluzba }}</b> · <code>GET {{ o.url }}</code></p>
                            <pre class="sos-portal-xml">{{ o.xml ?? o.chyba }}</pre>
                          }
                        </div>
                      </details>
                    }
                  }

                  <!-- komunita -->
                  <h3 class="govuk-heading-m">Komunita</h3>
                  <div class="sos-form-actions">
                    <a class="govuk-button govuk-button__basic" [href]="askUrl(z)">Opýtať sa v komunite</a>
                    <a class="govuk-button govuk-button--sec" [href]="searchUrl(z)">Podobné otázky vo fóre</a>
                  </div>
                  <p class="govuk-body-s sos-muted">Otázka sa otvorí vo fóre predvyplnená (číslo žiadosti, služba, predpis) – bez osobných údajov.
                    Z fóra sa vrátite tlačidlom „Agenda sociálnych služieb“ v hlavičke.</p>

                  <!-- zmena stavu -->
                  <h3 class="govuk-heading-m">Zmena stavu</h3>
                  @if (me()!.scope.mozeUpravovat) {
                    <div class="govuk-form-group">
                      <label class="govuk-label" for="novy-stav">Nový stav žiadosti</label>
                      <div class="sos-form-actions">
                        <select class="govuk-select" id="novy-stav" name="novy-stav" [(ngModel)]="newStatus">
                          <option value="NOVA">Nová</option>
                          <option value="V_POSUDZOVANI">V posudzovaní</option>
                          <option value="ROZHODNUTE">Rozhodnutá</option>
                        </select>
                        <button type="button" class="govuk-button govuk-button__basic sos-mb-0" (click)="saveStatus(z)" [disabled]="newStatus === z.stav">Uložiť</button>
                      </div>
                    </div>
                  } @else {
                    <p class="govuk-body sos-muted">Vaša skupina má k žiadostiam len čítací prístup.</p>
                  }
                </section>
              } @else {
                <h2 class="govuk-heading-m">Detail žiadosti</h2>
                <p class="sos-empty">Vyberte žiadosť v zozname.</p>
              }
            </div>
          </div>

          @if (me()!.scope.vsetko) {
            <hr class="govuk-section-break govuk-section-break--l govuk-section-break--visible">
            <section id="zmeny" aria-labelledby="zmeny-title">
              <h2 class="govuk-heading-l" id="zmeny-title">Zmeny v registroch IS CSRÚ <span class="sos-status sos-status--warning">mock</span></h2>
              <p class="govuk-body sos-muted">Zmenené subjekty v Evidencii ŤZP a pri peňažnom príspevku na opatrovanie za posledných 14 dní (GetListChanges).</p>
              @if (zmeny().length === 0) { <p class="sos-empty">Žiadne zmeny.</p> }
              @else {
                <table class="govuk-table sos-portal-table">
                  <caption class="govuk-table__caption govuk-visually-hidden">Zmeny v registroch</caption>
                  <thead class="govuk-table__head">
                    <tr class="govuk-table__row">
                      <th scope="col" class="govuk-table__header">Dátum a čas</th>
                      <th scope="col" class="govuk-table__header">Register</th>
                      <th scope="col" class="govuk-table__header">Typ zmeny</th>
                      <th scope="col" class="govuk-table__header">Osoba</th>
                    </tr>
                  </thead>
                  <tbody class="govuk-table__body">
                    @for (z of zmeny(); track z.cas + z.osoba) {
                      <tr class="govuk-table__row">
                        <td class="govuk-table__cell sos-nowrap">{{ z.cas | date:'d. M. yyyy HH:mm' }}</td>
                        <td class="govuk-table__cell">{{ z.register === 'TZP' ? 'Evidencia ŤZP' : 'Peňažný príspevok na opatrovanie' }}</td>
                        <td class="govuk-table__cell"><span class="sos-status" [class.sos-status--success]="z.typ === 'NOVY'" [class.sos-status--neutral]="z.typ !== 'NOVY'">{{ z.typ === 'NOVY' ? 'nový záznam' : 'zmena' }}</span></td>
                        <td class="govuk-table__cell">{{ z.osoba }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              }
            </section>
          }
        }
      </main>
    </div>

    <!-- ===================== PÄTA ===================== -->
    <footer class="govuk-footer" role="contentinfo">
      <div class="govuk-width-container">
        <div class="govuk-footer__meta">
          <div class="govuk-footer__meta-item govuk-footer__meta-item--grow">
            <h2 class="govuk-visually-hidden">Informácie o aplikácii</h2>
            <ul class="govuk-footer__inline-list">
              <li class="govuk-footer__inline-list-item"><a class="govuk-footer__link" [href]="forumUrl()">Komunitné fórum Platformy SOS</a></li>
              <li class="govuk-footer__inline-list-item"><a class="govuk-footer__link" href="https://www.slov-lex.sk" target="_blank" rel="noopener">Slov-Lex</a></li>
            </ul>
            <span class="govuk-footer__licence-description">
              Demo aplikácia (Spring Boot + Angular) k fóru Platformy SOS · SSO cez Keycloak · údaje sa držia len v pamäti.
              <br>Vytvorené v súlade s Jednotným dizajn manuálom elektronických služieb (ID-SK 3.0).
            </span>
          </div>
          <div class="govuk-footer__meta-item">
            <a class="govuk-footer__link govuk-footer__copyright-logo" href="/"><img src="assets/agenda-logo.svg" alt="Agenda sociálnych služieb"></a>
          </div>
        </div>
      </div>
    </footer>

    @if (toast()) {
      <div class="govuk-notification-banner govuk-notification-banner--success sos-portal-toast" role="status" aria-live="polite">
        <div class="govuk-notification-banner__content"><p class="govuk-body sos-mb-0">{{ toast() }}</p></div>
      </div>
    }
  `
})
export class AppComponent implements OnInit {
  private http = inject(HttpClient);
  private destroyRef = inject(DestroyRef);

  me = signal<Me | null>(null);
  loading = signal(true);
  forumUrl = signal('http://localhost:8000');
  cases = signal<Ziadost[]>([]);
  selectedId = signal<string | null>(null);
  toast = signal('');
  loggedOut = signal(false);
  govOpen = signal(false);
  menuOpen = signal(false);
  csru = signal<Csru | null>(null);
  csruLoading = signal(false);
  csruError = signal('');
  zmeny = signal<Zmena[]>([]);

  constructor() {
    // pri výbere žiadosti načítať referenčné údaje z IS CSRÚ
    effect(() => {
      const id = this.selectedId();
      if (id) {
        this.loadCsru(id);
        history.replaceState(null, '', '#' + id);
      }
    });
  }
  newStatus: Stav = 'NOVA';

  selected = computed(() => {
    const z = this.cases().find(c => c.id === this.selectedId()) ?? null;
    if (z) { this.newStatus = z.stav; }
    return z;
  });

  ngOnInit(): void {
    this.http.get<{ forumUrl: string }>('/api/config').subscribe(c => this.forumUrl.set(c.forumUrl));
    this.http.get<Me>('/api/me').subscribe({
      next: me => {
        this.me.set(me); this.loading.set(false); this.loadCases();
        if (me.scope.vsetko) {
          this.http.get<{ zmeny: Zmena[] }>('/api/csru/zmeny').subscribe({ next: r => this.zmeny.set(r.zmeny), error: () => {} });
        }
      },
      error: () => { this.me.set(null); this.loading.set(false); }
    });
    // Single logout: ak sa používateľ odhlási inde (napr. vo fóre), Keycloak zavolá /backchannel-logout
    // a server zruší reláciu. Bez pravidelného dopytovania – overíme to pri návrate na kartu
    // a pri každej odpovedi 401 z API.
    const onVisible = () => { if (document.visibilityState === 'visible') { this.checkSession(); } };
    document.addEventListener('visibilitychange', onVisible);
    window.addEventListener('focus', onVisible);
    this.destroyRef.onDestroy(() => {
      document.removeEventListener('visibilitychange', onVisible);
      window.removeEventListener('focus', onVisible);
    });
  }

  checkSession(): void {
    if (!this.me()) { return; }
    this.http.get<Me>('/api/me').subscribe({
      error: err => this.handleUnauthorized(err)
    });
  }

  /** 401 z API = relácia skončila (napr. odhlásenie vo fóre cez back-channel logout). */
  private handleUnauthorized(err: { status?: number }): boolean {
    if (err?.status !== 401 || !this.me()) { return false; }
    this.me.set(null);
    this.cases.set([]);
    this.selectedId.set(null);
    this.csru.set(null);
    this.zmeny.set([]);
    this.loggedOut.set(true);
    return true;
  }

  loadCases(): void {
    this.http.get<Ziadost[]>('/api/cases').subscribe(list => {
      this.cases.set(list);
      // návrat z fóra: portal/#Z-2026-0101 otvorí tú istú žiadosť
      const fromHash = decodeURIComponent(window.location.hash.replace(/^#/, ''));
      if (fromHash && list.some(c => c.id === fromHash)) {
        this.selectedId.set(fromHash);
      } else if (!this.selectedId() && list.length) { this.selectedId.set(list[0].id); }
    });
  }

  loadCsru(id: string): void {
    this.csru.set(null);
    this.csruError.set('');
    this.csruLoading.set(true);
    this.http.get<Csru>(`/api/cases/${id}/csru`).subscribe({
      next: c => { if (this.selectedId() === id) { this.csru.set(c); } this.csruLoading.set(false); },
      error: err => {
        this.csruLoading.set(false);
        if (!this.handleUnauthorized(err)) { this.csruError.set('Údaje z IS CSRÚ sa nepodarilo načítať'); }
      }
    });
  }

  saveStatus(z: Ziadost): void {
    this.http.put<Ziadost>(`/api/cases/${z.id}/status`, { stav: this.newStatus }).subscribe({
      next: updated => {
        this.cases.update(list => list.map(c => c.id === updated.id ? updated : c));
        this.showToast(`Stav žiadosti ${updated.id} zmenený na „${this.stavText(updated.stav)}“`);
      },
      error: err => { if (!this.handleUnauthorized(err)) { this.showToast('Zmena stavu sa nepodarila'); } }
    });
  }

  count(stav: Stav): number { return this.cases().filter(c => c.stav === stav).length; }
  urgent(): number { return this.cases().filter(c => this.isUrgent(c)).length; }
  isUrgent(z: Ziadost): boolean {
    const days = (new Date(z.termin).getTime() - Date.now()) / 86_400_000;
    return z.stav !== 'ROZHODNUTE' && days <= 14;
  }
  stavText(s: Stav): string { return STAV_TEXT[s]; }
  statusClass(s: Stav): string {
    return 'sos-status ' + (s === 'ROZHODNUTE' ? 'sos-status--success' : s === 'V_POSUDZOVANI' ? 'sos-status--warning' : 'sos-status--new');
  }
  initials(m: Me): string {
    return (m.name || m.username).split(/\s+/).map(p => p[0]).join('').slice(0, 2).toUpperCase();
  }

  slovLexUrl(z: Ziadost): string {
    return `https://www.slov-lex.sk/ezbierky/pravne-predpisy/SK/ZZ/${z.predpis.rok}/${z.predpis.cislo}/`;
  }

  /** Adresa, kam sa fórum vráti (odkaz „späť“ v hlavičke fóra) – aj s otvorenou žiadosťou. */
  backUrl(id?: string | null): string {
    return `${window.location.origin}/${id ? '#' + id : ''}`;
  }

  /** Fórum cez /signin (SSO) a potom späť na úvodnú stránku fóra s parametrom sos_back. */
  forumHomeUrl(): string {
    const back = new URLSearchParams({ sos_back: this.backUrl(this.selectedId()) });
    return `${this.forumUrl()}/signin?returnto=${encodeURIComponent('/questions?' + back.toString())}`;
  }

  askUrl(z: Ziadost): string {
    const title = `${z.sluzba}: otázka k ${z.predpis.paragraf} (žiadosť ${z.id})`;
    const body = [
      `Riešim žiadosť ${z.id} – ${z.sluzba} (${z.obec}).`,
      '',
      `Situácia: ${z.popis}`,
      '',
      `Súvisiaci predpis: ${z.predpis.paragraf} zákona č. ${z.predpis.cislo}/${z.predpis.rok} Z. z.`,
      '',
      'Otázka: …',
      '',
      '_Odoslané z aplikácie Agenda sociálnych služieb (demo). Neuvádzajte osobné údaje žiadateľa._'
    ].join('\n');
    const q = new URLSearchParams({ title, body, tags: z.tagy.join(','), sos_back: this.backUrl(z.id) });
    return `${this.forumUrl()}/questions/ask?${q.toString()}`;
  }

  searchUrl(z: Ziadost): string {
    const q = new URLSearchParams({ q: z.sluzba, sos_back: this.backUrl(z.id) });
    return `${this.forumUrl()}/search?${q.toString()}`;
  }

  private showToast(msg: string): void {
    this.toast.set(msg);
    setTimeout(() => this.toast.set(''), 3500);
  }
}
