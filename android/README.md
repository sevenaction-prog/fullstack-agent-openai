# Astra Android V1

Astra est la version mobile locale et sans abonnement du projet.

## Cible

- Samsung Galaxy Z Fold6 (arm64-v8a)
- Android 13+
- Assistant local après un téléchargement initial des modèles
- Aucun compte, aucune clé API, aucun abonnement

## Fonctions intégrées dans la V1

- noyau visuel animé avec états repos / écoute / réflexion / parole / réunion
- chat texte avec un modèle GGUF local via llama.cpp
- lecture vocale automatique des réponses via Android TextToSpeech
- push-to-talk via la reconnaissance vocale Android, en privilégiant le mode hors ligne quand disponible
- mémoire locale contrôlable par l’utilisateur
- installation des modèles au premier lancement
- mode Réunion via foreground service microphone
- enregistrement en segments PCM de 10 minutes pour limiter les pertes en cas d’interruption
- pause, marqueur et arrêt depuis la notification Android
- transcription locale post-réunion avec whisper.cpp
- résumé hiérarchique local : décisions, actions, responsables, dates et questions ouvertes

## Modèles téléchargés une seule fois

- unsloth/Qwen3.5-2B-GGUF : Qwen3.5-2B-Q4_K_M.gguf (~1,28 Go, Apache-2.0)
- ggerganov/whisper.cpp : ggml-base-q5_1.bin (~60 Mo)

Les modèles ne sont pas stockés dans Git.

## Confidentialité

La mémoire, les enregistrements de réunion, les transcriptions et les résumés sont conservés dans le stockage privé de l’application. Internet est nécessaire uniquement pour télécharger les modèles lors de la première installation de cette V1.

## Limites connues

- la saisie vocale courte dépend du service de reconnaissance vocale installé sur Android ; le mode hors ligne dépend du pack de langue présent sur le téléphone
- la séparation automatique des locuteurs n’est pas encore incluse
- le mot d’activation permanent est volontairement reporté jusqu’aux tests de batterie réels sur le Fold6
- l’interface Fold6 double panneau sera raffinée après validation du cœur fonctionnel
