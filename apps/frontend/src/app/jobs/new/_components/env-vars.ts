export type EnvVarRow = {
  id: string;
  key: string;
  value: string;
};

export const ENV_VAR_KEY_PATTERN = /^[a-zA-Z_][a-zA-Z0-9_]*$/;
export const MAX_ENV_VARS = 50;
export const MAX_ENV_KEY_LENGTH = 255;
export const MAX_ENV_VALUE_LENGTH = 4096;

const envVarFieldErrorMessages = new Map<string, string>([
  ['envVars.MAX_ENTRIES_EXCEEDED', 'Maximum 50 environment variables are allowed.'],
  ['envVars.INVALID_KEY', 'Environment variable keys must match ^[a-zA-Z_][a-zA-Z0-9_]*$.'],
  ['envVars.RESERVED_KEY', 'Environment variable keys cannot be JOB_ID or start with NOMAD_.'],
  ['envVars.KEY_TOO_LONG', 'Environment variable key length cannot exceed 255 characters.'],
  ['envVars.VALUE_TOO_LONG', 'Environment variable value length cannot exceed 4096 characters.'],
  ['envVars.MISSING_KEY', 'Environment variable key cannot be empty when value is provided.'],
  ['envVars.DUPLICATE_KEY', 'Environment variable keys must be unique.'],
]);

export function createEnvVarRow(): EnvVarRow {
  return {
    id: crypto.randomUUID(),
    key: '',
    value: '',
  };
}

export function toEnvVarFieldErrors(rows: EnvVarRow[]): Record<string, string[]> {
  const nonEmptyRows = rows.filter((row) => row.key.trim().length > 0 || row.value.length > 0);
  const errors: Record<string, string[]> = {};
  const seenKeys = new Set<string>();

  if (nonEmptyRows.length > MAX_ENV_VARS) {
    errors['envVars.MAX_ENTRIES_EXCEEDED'] = [
      envVarFieldErrorMessages.get('envVars.MAX_ENTRIES_EXCEEDED') ?? 'Invalid environment variables.',
    ];
  }

  for (const row of nonEmptyRows) {
    const normalizedKey = row.key.trim();

    if (!normalizedKey) {
      errors['envVars.MISSING_KEY'] = [envVarFieldErrorMessages.get('envVars.MISSING_KEY') ?? 'Invalid environment variables.'];
      continue;
    }

    if (normalizedKey.length > MAX_ENV_KEY_LENGTH) {
      errors['envVars.KEY_TOO_LONG'] = [envVarFieldErrorMessages.get('envVars.KEY_TOO_LONG') ?? 'Invalid environment variables.'];
    }

    if (!ENV_VAR_KEY_PATTERN.test(normalizedKey)) {
      errors['envVars.INVALID_KEY'] = [envVarFieldErrorMessages.get('envVars.INVALID_KEY') ?? 'Invalid environment variables.'];
    }

    if (normalizedKey === 'JOB_ID' || normalizedKey.startsWith('NOMAD_')) {
      errors['envVars.RESERVED_KEY'] = [envVarFieldErrorMessages.get('envVars.RESERVED_KEY') ?? 'Invalid environment variables.'];
    }

    if (row.value.length > MAX_ENV_VALUE_LENGTH) {
      errors['envVars.VALUE_TOO_LONG'] = [
        envVarFieldErrorMessages.get('envVars.VALUE_TOO_LONG') ?? 'Invalid environment variables.',
      ];
    }

    if (seenKeys.has(normalizedKey)) {
      errors['envVars.DUPLICATE_KEY'] = [
        envVarFieldErrorMessages.get('envVars.DUPLICATE_KEY') ?? 'Invalid environment variables.',
      ];
      continue;
    }

    seenKeys.add(normalizedKey);
  }

  return errors;
}

export function toEnvVarsMap(rows: EnvVarRow[]): Record<string, string> {
  const payload: Record<string, string> = {};

  for (const row of rows) {
    const key = row.key.trim();
    if (!key && row.value.length === 0) {
      continue;
    }
    if (!key) {
      continue;
    }
    payload[key] = row.value;
  }

  return payload;
}

export function collectEnvVarErrorMessages(fieldErrors: Record<string, string[] | undefined>): string[] {
  return Object.entries(fieldErrors)
    .filter(([key]) => key.startsWith('envVars.'))
    .flatMap(([, messages]) => messages ?? []);
}
