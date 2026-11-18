function safeCell(value) {
  let text = String(value ?? "");
  // Prefix spreadsheet formula triggers (including those preceded by whitespace).
  if (["=", "+", "-", "@"].includes(text.trimStart()[0])) text = "'" + text;
  return '"' + text.replace(/"/g, '""') + '"';
}

export function buildAttendanceCsv(rows) {
  return "\uFEFF" + rows.map((row) => row.map(safeCell).join(",")).join("\r\n");
}
