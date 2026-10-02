#!/usr/bin/env bash
# Platforma SOS – zapne jednotné odhlásenie (OIDC Back-Channel Logout) pre klientov "scoold" a "portal-demo"
# v už bežiacom Keycloaku (realm sos). Pri novom importe realmu to netreba – je to v keycloak/sos-realm.json.
# Spustenie z koreňa repozitára:  bash keycloak/enable-backchannel-logout.sh
set -euo pipefail
kc() { docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh "$@"; }

kc config credentials --server http://localhost:8080 --realm master --user admin --password "${KC_ADMIN_PASSWORD:-admin}"

enable() {
	local client="$1" url="$2" id
	id=$(kc get clients -r sos -q clientId="$client" --fields id --format csv --noquotes | tr -d '\r' | head -n1)
	if [ -z "$id" ]; then
		echo "Klient $client v realme sos neexistuje – preskakujem." >&2
		return
	fi
	kc update "clients/$id" -r sos \
		-s frontchannelLogout=false \
		-s "attributes.\"backchannel.logout.url\"=$url" \
		-s 'attributes."backchannel.logout.session.required"=true' \
		-s 'attributes."backchannel.logout.revoke.offline.tokens"=false'
	echo "OK: $client -> $url"
}

# adresy sú interné v docker sieti – volá ich kontajner Keycloaku, nie prehliadač
enable scoold      http://scoold:8000/sos/backchannel-logout
enable portal-demo http://portal:8082/backchannel-logout
