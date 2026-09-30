<#--
  Platforma SOS – layout prihlasovacích stránok Keycloaku v štýle ID-SK 3.0.
  Vychádza zo šablóny base/login/template.ftl (Keycloak 26.3) – zachované sú všetky sekcie
  (header, form, info, socialProviders, show-username) a skripty Keycloaku; zmenený je len vzhľad.
  Pri aktualizácii Keycloaku porovnajte s novou verziou base/login/template.ftl.
-->
<#import "footer.ftl" as loginFooter>
<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html class="${properties.kcHtmlClass!}" lang="${lang}"<#if realm.internationalizationEnabled> dir="${(locale.rtl)?then('rtl','ltr')}"</#if>>

<head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="robots" content="noindex, nofollow">
    <#if properties.meta?has_content>
        <#list properties.meta?split(' ') as meta>
            <meta name="${meta?split('==')[0]}" content="${meta?split('==')[1]}"/>
        </#list>
    </#if>
    <meta name="theme-color" content="#072c66">
    <title>${msg("loginTitle",(realm.displayName!''))}</title>
    <link rel="icon" href="${url.resourcesPath}/img/favicon.ico" />
    <#if properties.stylesCommon?has_content>
        <#list properties.stylesCommon?split(' ') as style>
            <link href="${url.resourcesCommonPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if properties.scripts?has_content>
        <#list properties.scripts?split(' ') as script>
            <script src="${url.resourcesPath}/${script}" type="text/javascript"></script>
        </#list>
    </#if>
    <script type="importmap">
        {
            "imports": {
                "rfc4648": "${url.resourcesCommonPath}/vendor/rfc4648/rfc4648.js"
            }
        }
    </script>
    <script src="${url.resourcesPath}/js/menu-button-links.js" type="module"></script>
    <#if scripts??>
        <#list scripts as script>
            <script src="${script}" type="text/javascript"></script>
        </#list>
    </#if>
    <script type="module">
        import { startSessionPolling } from "${url.resourcesPath}/js/authChecker.js";

        startSessionPolling(
            "${url.ssoLoginInOtherTabsUrl?no_esc}"
        );
    </script>
    <script type="module">
        document.addEventListener("click", (event) => {
            const link = event.target.closest("a[data-once-link]");
            if (!link) {
                return;
            }
            if (link.getAttribute("aria-disabled") === "true") {
                event.preventDefault();
                return;
            }
            const { disabledClass } = link.dataset;
            if (disabledClass) {
                link.classList.add(...disabledClass.trim().split(/\s+/));
            }
            link.setAttribute("role", "link");
            link.setAttribute("aria-disabled", "true");
        });
        // ID-SK: rozbalenie informácie "Oficiálna stránka verejnej správy SR"
        const govBtn = document.querySelector(".idsk-secondary-navigation__heading-button");
        const govBody = document.querySelector(".idsk-secondary-navigation__body");
        if (govBtn && govBody) {
            govBtn.addEventListener("click", () => {
                const open = govBtn.getAttribute("aria-expanded") !== "true";
                govBtn.setAttribute("aria-expanded", String(open));
                govBody.classList.toggle("hidden", !open);
            });
        }
    </script>
    <#if authenticationSession??>
        <script type="module">
            import { checkAuthSession } from "${url.resourcesPath}/js/authChecker.js";

            checkAuthSession(
                "${authenticationSession.authSessionIdHash}"
            );
        </script>
    </#if>
</head>

<body class="${properties.kcBodyClass!}" data-page-id="login-${pageId}">
<a href="#kc-content" class="govuk-skip-link">${msg("sosSkip")}</a>

<#-- ===== Hlavička ID-SK ===== -->
<div class="govuk-header__wrapper">
    <header class="govuk-header idsk-shadow-head">
        <div class="govuk-header__container">
            <div class="idsk-secondary-navigation govuk-width-container">
                <div class="idsk-secondary-navigation__header">
                    <div class="idsk-secondary-navigation__heading">
                        <div class="idsk-secondary-navigation__heading-title">
                            <span class="idsk-secondary-navigation__heading-mobile">SK</span>
                            <span class="idsk-secondary-navigation__heading-desktop">${msg("sosOfficial")}</span>
                            <button type="button" class="govuk-button govuk-button--texted--inverse idsk-secondary-navigation__heading-button" aria-expanded="false">
                                <span class="idsk-secondary-navigation__heading-mobile">e-Gov</span>
                                <span class="idsk-secondary-navigation__heading-desktop"><b>${msg("sosOfficialGov")}</b></span>
                                <span class="material-icons" aria-hidden="true">arrow_drop_down</span>
                            </button>
                        </div>
                        <div class="idsk-secondary-navigation__body hidden">
                            <div class="idsk-secondary-navigation__text">
                                <div>
                                    <h3 class="govuk-body-s"><b>${msg("sosOfficialDomainTitle")}</b></h3>
                                    <p class="govuk-body-s">${msg("sosOfficialDomainText")}</p>
                                </div>
                                <div>
                                    <h3 class="govuk-body-s"><b>${msg("sosOfficialSecureTitle")}</b></h3>
                                    <p class="govuk-body-s">${msg("sosOfficialSecureText")}</p>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <div class="govuk-predheader govuk-width-container">
            <div class="govuk-header__logo">
                <span class="govuk-header__link govuk-header__link--homepage sos-kc-logo">
                    <img src="${url.resourcesPath}/img/sos-logo.svg" alt="${(realm.displayName!'Platforma SOS')}"/>
                </span>
            </div>
            <#if realm.internationalizationEnabled && locale.supported?size gt 1>
                <div class="${properties.kcLocaleMainClass!}" id="kc-locale">
                    <div id="kc-locale-wrapper">
                        <div id="kc-locale-dropdown" class="menu-button-links">
                            <button tabindex="1" id="kc-current-locale-link" class="govuk-button govuk-button--texted" aria-label="${msg("languages")}" aria-haspopup="true" aria-expanded="false" aria-controls="language-switch1">${locale.current}<span class="material-icons" aria-hidden="true">arrow_drop_down</span></button>
                            <ul role="menu" tabindex="-1" aria-labelledby="kc-current-locale-link" aria-activedescendant="" id="language-switch1" class="${properties.kcLocaleListClass!}">
                                <#assign i = 1>
                                <#list locale.supported as l>
                                    <li role="none">
                                        <a role="menuitem" id="language-${i}" class="${properties.kcLocaleItemClass!}" href="${l.url}">${l.label}</a>
                                    </li>
                                    <#assign i++>
                                </#list>
                            </ul>
                        </div>
                    </div>
                </div>
            </#if>
        </div>
    </header>
</div>

<#-- ===== Obsah ===== -->
<div class="govuk-width-container">
<main class="govuk-main-wrapper ${properties.kcLoginClass!}" id="kc-content" tabindex="-1">
  <div class="govuk-grid-row">
    <div class="govuk-grid-column-two-thirds-from-desktop">
    <div class="${properties.kcFormCardClass!}">
        <header class="${properties.kcFormHeaderClass!}">
        <#if !(auth?has_content && auth.showUsername() && !auth.showResetCredentials())>
            <h1 id="kc-page-title" class="govuk-heading-xl"><#nested "header"></h1>
            <#if (pageId!"") == "login.ftl" || (pageId!"") == "login"><p class="govuk-body-l">${msg("sosLead")}</p></#if>
            <#if displayRequiredFields>
                <p class="govuk-body-s sos-kc-required"><span class="required">*</span> ${msg("requiredFields")}</p>
            </#if>
        <#else>
            <#nested "show-username">
            <div id="kc-username" class="sos-kc-username">
                <label id="kc-attempted-username" class="govuk-body-l">${auth.attemptedUsername}</label>
                <a id="reset-login" class="govuk-link" href="${url.loginRestartFlowUrl}" aria-label="${msg("restartLoginTooltip")}">${msg("restartLoginTooltip")}</a>
            </div>
            <#if displayRequiredFields>
                <p class="govuk-body-s sos-kc-required"><span class="required">*</span> ${msg("requiredFields")}</p>
            </#if>
        </#if>
        </header>

        <div id="kc-content-wrapper">
          <#-- App-initiated actions should not see warning messages about the need to complete the action during login. -->
          <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
              <div class="${properties.kcAlertClass!} sos-kc-alert--${message.type}" role="alert">
                  <div class="govuk-notification-banner__content">
                      <p class="${properties.kcAlertTitleClass!}">${kcSanitize(message.summary)?no_esc}</p>
                  </div>
              </div>
          </#if>

          <#nested "form">

          <#if auth?has_content && auth.showTryAnotherWayLink()>
              <form id="kc-select-try-another-way-form" action="${url.loginAction}" method="post">
                  <div class="${properties.kcFormGroupClass!}">
                      <input type="hidden" name="tryAnotherWay" value="on"/>
                      <a href="#" id="try-another-way" class="govuk-link"
                         onclick="document.forms['kc-select-try-another-way-form'].requestSubmit();return false;">${msg("doTryAnotherWay")}</a>
                  </div>
              </form>
          </#if>

          <#nested "socialProviders">

          <#if displayInfo>
              <div id="kc-info" class="${properties.kcSignUpClass!}">
                  <div id="kc-info-wrapper">
                      <#nested "info">
                  </div>
              </div>
          </#if>
        </div>

        <@loginFooter.content/>
    </div>
    </div>
  </div>
</main>
</div>

<#-- ===== Päta ID-SK ===== -->
<footer class="govuk-footer" role="contentinfo">
    <div class="govuk-width-container">
        <div class="govuk-footer__meta">
            <div class="govuk-footer__meta-item govuk-footer__meta-item--grow">
                <span class="govuk-footer__licence-description">
                    ${msg("sosOperator")}<br/>${msg("sosManual")}
                </span>
            </div>
            <div class="govuk-footer__meta-item">
                <img src="${url.resourcesPath}/img/sos-logo.svg" alt="" class="sos-kc-footer-logo"/>
            </div>
        </div>
    </div>
</footer>
</body>
</html>
</#macro>
