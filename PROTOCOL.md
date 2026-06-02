# Protocole ModChecker

Contrat **figé** partagé par le mod client et le plugin serveur. Les channels ne sont PAS
configurables côté serveur : le mod est compilé avec, les deux côtés doivent rester synchrones.

## Handshake

Le client ne révèle ses mods qu'à un serveur qui fait tourner le plugin. La détection se fait via
le **handshake natif de Minecraft** (`minecraft:register`) : quand le plugin enregistre le channel
entrant `modchecker:modlist`, le serveur l'annonce au client. Le client n'a donc **pas besoin de
recevoir un paquet** du serveur — il lui suffit de constater que le serveur accepte le channel.

```
joueur rejoint
      │
serveur ──[ minecraft:register: modchecker:modlist ]──▶ client   (le serveur a le plugin)
      │
client ──[ modchecker:modlist (C2S) ]──▶ serveur                 (au JOIN : voici mes mods)
```

- Serveur **avec** le plugin → channel annoncé → le client envoie sa liste au JOIN.
- Serveur **sans** le plugin (ou vanilla) → channel jamais annoncé → le client n'envoie **rien**
  (confidentialité préservée).
- Pas de modlist reçue dans le délai serveur → le joueur est considéré sans mod.

> **Note (historique) :** une première version reposait sur un paquet `modchecker:hello` (S2C). Les
> serveurs Bukkit/Paper **ne délivrent pas de façon fiable** les paquets custom S2C aux clients
> Fabric/NeoForge ; le handshake utilise donc l'annonce de channel native, fiable et standard. Le
> serveur peut encore émettre `hello` (ignoré par le client).

## Channels

| Channel | Sens | Quand | Payload |
|---------|------|-------|---------|
| `modchecker:modlist` | Client → Serveur (C2S) | au JOIN, si le serveur accepte le channel | tableau JSON des mods (String MC) |
| `modchecker:hello` | Serveur → Client (S2C) | au join (legacy, ignoré par le client) | version du plugin (String MC) |

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
