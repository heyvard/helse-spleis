Jobs
====


# Starte feriepenger

```
% ./deploy_jobb.sh
🐳 Image: imagenavnet mitt
☸️ Cluster (1: dev-gcp | 2: prod-gcp): 2
🔑 API key: <hemmelig>
🔑 Parallelism: (30 default) 
🛠️ Hvilken jobb skal du kjøre? feriepenger
🪪 Hva skal arbeidId settes til? fp2025
🏜️ Dryrun? (Y/n): n
🎒 Eventuelt andre parametre til jobben? 2024

Når jobben er ferdig, husk å kjøre
  kubectl delete naisjob spleis-migrate
```

API-key hentes i [Nais-konsollet](https://console.nav.cloud.nais.io/team/tbd/settings). 

Dryrun settes til Y om du ønsker å teste jobben. Dette forutsetter at jobbe skjønner hva dryrun er.