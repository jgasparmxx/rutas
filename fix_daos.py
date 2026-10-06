import re

file_path = "app/src/main/java/com/gaspar/rutassendero/ui/RutasViewModel.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

senderista_funcs = ["getSenderistaFlow", "getFirstSenderista", "getSenderistaByEmailFlow", "getSenderistaByEmail", "getAllSenderistas", "deleteAllSenderistas", "clearCachedSenderistas", "insertSenderista", "getSenderistaByTelefono", "getSenderistaByBeaconId"]
eventos_funcs = ["getInvitacionesAceptadasFlow", "getInvitacionesAceptadas", "getInvitacionAceptadaById", "insertInvitacionAceptada", "deleteInvitacionAceptada", "getEventosFlow", "getEventos", "getEventoById", "insertEvento", "deleteEvento"]
participantes_funcs = ["getAllParticipantesFlow", "getAllParticipantes", "getParticipanteByBeaconId", "getParticipanteByTelefono", "insertParticipante", "insertParticipantes", "deleteParticipante", "pruneExpiredParticipantes", "clearParticipantes", "getAgrupacionesFlow", "getAgrupaciones", "insertAgrupaciones", "deleteAllAgrupaciones"]
frases_funcs = ["getAllFrases", "getFrasesForCategory", "getRandomFraseForCategory", "getFraseCount", "insertFrases"]

for func in senderista_funcs:
    content = re.sub(rf"db\.rutasDao\(\)\.{func}", f"db.senderistaDao().{func}", content)

for func in eventos_funcs:
    content = re.sub(rf"db\.rutasDao\(\)\.{func}", f"db.eventosDao().{func}", content)

for func in participantes_funcs:
    content = re.sub(rf"db\.rutasDao\(\)\.{func}", f"db.participantesDao().{func}", content)

for func in frases_funcs:
    content = re.sub(rf"db\.rutasDao\(\)\.{func}", f"db.frasesDao().{func}", content)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)
print("Reemplazos realizados en RutasViewModel.kt")
