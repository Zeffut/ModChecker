# Protocole ModChecker

Contrat **figé** partagé par le mod client et le plugin serveur. Les channels ne sont PAS
configurables côté serveur : le mod est compilé avec, les deux côtés doivent rester synchrones.

## Handshake

Le client ne révèle ses mods qu'à un serveur qui a prouvé avoir le plugin :

```
joueur rejoint
      │
serveur ──[ modchecker:hello (S2C) ]──▶ client          (preuve : le plugin est là)
      │
client ──[ modchecker:modlist (C2S) ]──▶ serveur         (réponse : voici mes mods)
```

- Pas de hello reçu (plugin absent / serveur vanilla) → le client n'envoie **rien**.
- Client vanilla (sans le mod) → reçoit le hello sur un channel inconnu, l'**ignore** silencieusement.
- Pas de modlist reçue dans le délai serveur → le joueur est considéré sans mod.

## Channels

| Channel | Sens | Quand | Payload |
|---------|------|-------|---------|
| `modchecker:hello` | Serveur → Client (S2C) | au join | version du plugin (String MC) |
| `modchecker:modlist` | Client → Serveur (C2S) | en réponse au hello | tableau JSON des mods (String MC) |

## Payload `modchecker:modlist`
Un **String Minecraft unique** : `VarInt(longueur UTF-8)` suivi des octets UTF-8.
Le contenu du String est un tableau JSON :

```json
[
  {"id": "fabric-api", "name": "Fabric API", "version": "0.141.3"},
  {"id": "sodium", "name": "Sodium", "version": "0.6.0"}
]
```

- `id` (obligatoire) : identifiant du mod
- `name` (obligatoire) : nom lisible
- `version` (optionnel) : version ; absent → le serveur stocke `"?"`

## Payload `modchecker:hello`
Un **String Minecraft unique** = la version du plugin (ex. `"1.0.0"`). Le client peut l'ignorer
aujourd'hui ; il sert de point d'extension pour une vérification de compatibilité future.

## Encodage (même format binaire des deux côtés)
- **Fabric** : `PacketCodecs.STRING` (= VarInt + UTF-8).
- **NeoForge** : `ByteBufCodecs.STRING_UTF8` (= VarInt + UTF-8).
- **Plugin** : `ModListCodec.decode(byte[])` (réception modlist) et `ModListCodec.encode(String)`
  (envoi hello), tous deux en VarInt + UTF-8.
