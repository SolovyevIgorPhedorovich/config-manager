// Достаёт краткое сообщение об ошибке из ответа backend (поле message,
// которое отдаёт GlobalExceptionHandler), с запасным текстом для интерфейса.
export function getErrorMessage(err: any, fallback: string): string {
  return err?.response?.data?.message || err?.message || fallback;
}
