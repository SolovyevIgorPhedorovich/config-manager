import React, { useMemo, useState } from 'react';
import { Button, Space, Switch, Tag, Tooltip, Typography, message } from 'antd';
import {
  ArrowLeftOutlined, ArrowRightOutlined, CopyOutlined,
  CheckOutlined, FilterOutlined,
} from '@ant-design/icons';

const { Text } = Typography;

// ─────────────────────────────────────────────────────────────────────────────
// Типы
// ─────────────────────────────────────────────────────────────────────────────

type DiffStatus = 'unchanged' | 'changed' | 'left-only' | 'right-only';

interface DiffRow {
  key: string;
  leftValue: string;   // '' если ключа нет в левом конфиге
  rightValue: string;  // '' если ключа нет в правом конфиге
  hasLeft: boolean;
  hasRight: boolean;
  status: DiffStatus;
}

export interface ConfigDiffViewerProps {
  leftConfig:  Record<string, any> | null;
  rightConfig: Record<string, any> | null;
  leftLabel?:  string;
  rightLabel?: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Утилиты
// ─────────────────────────────────────────────────────────────────────────────

/** Рекурсивно разворачивает JSON-объект в плоский Record<string, string>.
 *  Массивы отображаются как JSON-строка (атомарное значение).
 *  Вложенные объекты разворачиваются через точку: "interface.mode" */
function flatten(obj: any, prefix = ''): Record<string, string> {
  if (obj === null || obj === undefined)  return prefix ? { [prefix]: 'null' } : {};
  if (typeof obj !== 'object')            return { [prefix]: String(obj) };
  if (Array.isArray(obj))                 return { [prefix]: JSON.stringify(obj) };

  const result: Record<string, string> = {};
  for (const [k, v] of Object.entries(obj)) {
    const path = prefix ? `${prefix}.${k}` : k;
    if (v !== null && typeof v === 'object' && !Array.isArray(v)) {
      Object.assign(result, flatten(v, path));
    } else {
      result[path] = v === null ? 'null'
                   : Array.isArray(v) ? JSON.stringify(v)
                   : String(v);
    }
  }
  return result;
}

function computeDiff(
  left:  Record<string, string>,
  right: Record<string, string>,
): DiffRow[] {
  const allKeys = Array.from(new Set([...Object.keys(left), ...Object.keys(right)])).sort();
  return allKeys.map(key => {
    const hasLeft  = key in left;
    const hasRight = key in right;
    const lv = hasLeft  ? left[key]  : '';
    const rv = hasRight ? right[key] : '';

    let status: DiffStatus;
    if (!hasLeft)       status = 'right-only';
    else if (!hasRight) status = 'left-only';
    else if (lv === rv) status = 'unchanged';
    else                status = 'changed';

    return { key, leftValue: lv, rightValue: rv, hasLeft, hasRight, status };
  });
}

/** Конвертирует плоский Record<string,string> обратно в JSON-объект.
 *  Простая версия: работает с одним уровнем вложенности через точку. */
function unflatten(flat: Record<string, string>): Record<string, any> {
  const result: Record<string, any> = {};
  for (const [path, val] of Object.entries(flat)) {
    const parts = path.split('.');
    let cur: any = result;
    for (let i = 0; i < parts.length - 1; i++) {
      if (!(parts[i] in cur)) cur[parts[i]] = {};
      cur = cur[parts[i]];
    }
    // Try to parse JSON values (arrays, booleans, numbers)
    try { cur[parts[parts.length - 1]] = JSON.parse(val); }
    catch { cur[parts[parts.length - 1]] = val; }
  }
  return result;
}

function displayValue(val: string, maxLen = 80): string {
  if (!val || val === 'null') return '—';
  return val.length > maxLen ? val.slice(0, maxLen) + '…' : val;
}

// ─────────────────────────────────────────────────────────────────────────────
// Стили
// ─────────────────────────────────────────────────────────────────────────────

const BG: Record<DiffStatus, { l: string; r: string }> = {
  unchanged: { l: 'transparent', r: 'transparent' },
  changed:   { l: '#ffeef0',     r: '#e6ffed' },
  'left-only':  { l: '#ffeef0',  r: 'transparent' },
  'right-only': { l: 'transparent', r: '#e6ffed' },
};

const TEXT: Record<DiffStatus, { l: string; r: string }> = {
  unchanged:    { l: '#595959', r: '#595959' },
  changed:      { l: '#a61d24', r: '#237804' },
  'left-only':  { l: '#a61d24', r: '#8c8c8c' },
  'right-only': { l: '#8c8c8c', r: '#237804' },
};

const STATUS_TAG: Record<DiffStatus, { color: string; label: string }> = {
  unchanged:    { color: 'default',  label: '='  },
  changed:      { color: 'warning',  label: '~'  },
  'left-only':  { color: 'error',    label: '−'  },
  'right-only': { color: 'success',  label: '+'  },
};

// Цвет текста ключа в колонке «КЛЮЧ / ДЕЙСТВИЕ» — подсвечивается по статусу,
// как и значения: оранжевый — изменено, красный — удалено, зелёный — добавлено.
const KEY_TEXT: Record<DiffStatus, string> = {
  unchanged:    '#434343',
  changed:      '#d46b08',
  'left-only':  '#a61d24',
  'right-only': '#237804',
};

// ─────────────────────────────────────────────────────────────────────────────
// Компонент
// ─────────────────────────────────────────────────────────────────────────────

export default function ConfigDiffViewer({
  leftConfig, rightConfig, leftLabel = 'Предыдущая', rightLabel = 'Текущая',
}: ConfigDiffViewerProps) {

  const [onlyDiffs, setOnlyDiffs] = useState(true);
  // proposed: ключи переопределены пользователем. '__del__' = удалено из предложенного
  const [proposed, setProposed] = useState<Record<string, string>>({});

  const flatLeft  = useMemo(() => flatten(leftConfig  ?? {}), [leftConfig]);
  const flatRight = useMemo(() => flatten(rightConfig ?? {}), [rightConfig]);
  const allRows   = useMemo(() => computeDiff(flatLeft, flatRight), [flatLeft, flatRight]);

  const stats = useMemo(() => ({
    changed:  allRows.filter(r => r.status === 'changed').length,
    added:    allRows.filter(r => r.status === 'right-only').length,
    removed:  allRows.filter(r => r.status === 'left-only').length,
    total:    allRows.filter(r => r.status !== 'unchanged').length,
  }), [allRows]);

  const visibleRows = useMemo(
    () => onlyDiffs ? allRows.filter(r => r.status !== 'unchanged') : allRows,
    [allRows, onlyDiffs],
  );

  // proposedRight = right config с учётом изменений пользователя
  const proposedRight = useMemo(() => {
    const base = { ...flatRight };
    for (const [key, val] of Object.entries(proposed)) {
      if (val === '__del__') delete base[key];
      else base[key] = val;
    }
    return base;
  }, [flatRight, proposed]);

  // Сброс предложенного конфига
  const resetProposed = () => setProposed({});

  const acceptLeft = (row: DiffRow) => {
    if (row.status === 'right-only') {
      // убираем ключ из предложенного (отменяем добавление)
      setProposed(p => ({ ...p, [row.key]: '__del__' }));
    } else {
      // копируем левое значение в предложенное
      setProposed(p => ({ ...p, [row.key]: row.leftValue }));
    }
  };

  const acceptRight = (row: DiffRow) => {
    if (row.status === 'left-only') {
      // восстанавливаем ключ (добавляем его в предложенное)
      setProposed(p => ({ ...p, [row.key]: row.leftValue }));
    } else {
      // возвращаемся к правому значению (убираем override)
      setProposed(p => { const n = { ...p }; delete n[row.key]; return n; });
    }
  };

  const copyProposed = () => {
    const json = JSON.stringify(unflatten(proposedRight), null, 2);
    navigator.clipboard.writeText(json)
      .then(() => message.success('Предложенная конфигурация скопирована в буфер'))
      .catch(() => message.error('Не удалось скопировать'));
  };

  // Определяем, изменена ли строка в proposed относительно правого конфига
  const isOverridden = (key: string) => key in proposed;

  if (!leftConfig && !rightConfig) {
    return <Text type="secondary">Выберите версию для сравнения</Text>;
  }

  return (
    <div style={{ fontFamily: 'monospace', fontSize: 13 }}>

      {/* ── Статистика и управление ── */}
      <div style={{
        display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 12,
        marginBottom: 12, padding: '8px 12px',
        background: '#f5f5f5', borderRadius: 6, border: '1px solid #e8e8e8',
      }}>
        <Space size={4}>
          <Tag color="warning"  icon={<span>~</span>}>{stats.changed}  изменено</Tag>
          <Tag color="error"    icon={<span>−</span>}>{stats.removed} удалено</Tag>
          <Tag color="success"  icon={<span>+</span>}>{stats.added}   добавлено</Tag>
          {stats.total === 0 && <Tag color="default">Конфигурации идентичны</Tag>}
        </Space>

        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 8 }}>
          {Object.keys(proposed).length > 0 && (
            <Button size="small" onClick={resetProposed}>Сбросить правки</Button>
          )}
          <Tooltip title="Скопировать предложенную (правую) конфигурацию как JSON">
            <Button size="small" icon={<CopyOutlined />} onClick={copyProposed}>
              Копировать JSON
            </Button>
          </Tooltip>
          <Space size={4}>
            <FilterOutlined style={{ color: '#8c8c8c' }} />
            <Text style={{ fontSize: 12, color: '#8c8c8c' }}>Только различия</Text>
            <Switch size="small" checked={onlyDiffs} onChange={setOnlyDiffs} />
          </Space>
        </div>
      </div>

      {/* ── Заголовки панелей (ключи слева) ── */}
      <div style={{ display: 'flex', borderBottom: '2px solid #e0e0e0', marginBottom: 0 }}>
        <div style={{ width: 220, flexShrink: 0, background: '#fafafa', borderRight: '1px solid #e0e0e0', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '6px 10px' }}>
          <Text style={{ fontSize: 11, color: '#8c8c8c' }}>КЛЮЧ / ДЕЙСТВИЕ</Text>
        </div>
        <div style={headerStyle('#f3f3f3')}>{leftLabel}</div>
        <div style={headerStyle('#f3f3f3')}>{rightLabel}</div>
      </div>

      {/* ── Строки diff ── */}
      <div style={{ border: '1px solid #e0e0e0', borderTop: 'none', borderRadius: '0 0 6px 6px', overflow: 'hidden' }}>
        {visibleRows.length === 0 && (
          <div style={{ padding: 24, textAlign: 'center', color: '#8c8c8c' }}>
            <CheckOutlined style={{ marginRight: 6 }} />Все параметры совпадают
          </div>
        )}

        {visibleRows.map((row, idx) => {
          const overridden = isOverridden(row.key);
          const proposedVal = overridden ? proposed[row.key] : null;
          const isDeleted = proposedVal === '__del__';

          const leftBg  = overridden ? '#fff9c4' : BG[row.status].l;
          const rightBg = overridden ? '#fff9c4' : BG[row.status].r;
          const leftClr  = TEXT[row.status].l;
          const rightClr = TEXT[row.status].r;
          const tag = STATUS_TAG[row.status];

          // Показываем значение правой панели с учётом proposed
          const effectiveRight = overridden
            ? (isDeleted ? <Text type="secondary" style={{ fontStyle: 'italic' }}>удалено</Text> : <span style={{ color: '#8a6d00' }}>{displayValue(proposedVal!)}</span>)
            : (row.hasRight ? displayValue(row.rightValue) : <Text type="secondary">—</Text>);

          return (
            <div key={row.key} style={{
              display: 'flex',
              borderBottom: idx < visibleRows.length - 1 ? '1px solid #f0f0f0' : 'none',
              minHeight: 32,
            }}>
              {/* ── Колонка ключа + стрелки (слева) ── */}
              <div style={{
                width: 220, flexShrink: 0,
                background: '#fafafa',
                borderRight: '1px solid #e8e8e8',
                display: 'flex', alignItems: 'center', gap: 4, padding: '2px 6px',
                overflow: 'hidden',
              }}>
                {/* Тег статуса */}
                <Tag color={tag.color} style={{ fontFamily: 'monospace', fontSize: 11, marginRight: 0, flexShrink: 0 }}>
                  {tag.label}
                </Tag>

                {/* Имя ключа — цвет по статусу изменения */}
                <Tooltip title={row.key}>
                  <Text style={{
                    fontSize: 11, flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                    color: overridden ? '#8a6d00' : KEY_TEXT[row.status],
                    fontWeight: row.status === 'unchanged' ? 400 : 600,
                  }}>
                    {row.key}
                  </Text>
                </Tooltip>

                {/* Кнопки принятия */}
                {row.status !== 'unchanged' && (
                  <Space size={2} style={{ flexShrink: 0 }}>
                    {/* ← Принять предыдущее / отменить добавление */}
                    <Tooltip title={row.status === 'right-only' ? 'Отменить добавление' : 'Принять предыдущее значение'}>
                      <Button
                        size="small" type="text"
                        icon={<ArrowLeftOutlined />}
                        style={{ color: '#a61d24', padding: '0 3px', height: 20, fontSize: 11 }}
                        onClick={() => acceptLeft(row)}
                      />
                    </Tooltip>
                    {/* → Принять текущее / восстановить */}
                    <Tooltip title={row.status === 'left-only' ? 'Восстановить ключ' : 'Принять текущее значение'}>
                      <Button
                        size="small" type="text"
                        icon={<ArrowRightOutlined />}
                        style={{ color: '#237804', padding: '0 3px', height: 20, fontSize: 11 }}
                        onClick={() => acceptRight(row)}
                      />
                    </Tooltip>
                  </Space>
                )}
              </div>

              {/* ── Левая панель (предыдущая) ── */}
              <div style={{ flex: 1, background: leftBg, padding: '4px 10px', display: 'flex', alignItems: 'center', overflow: 'hidden' }}>
                {row.hasLeft
                  ? <span style={{ color: leftClr, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{displayValue(row.leftValue)}</span>
                  : <span style={{ color: '#bfbfbf', fontStyle: 'italic' }}>—</span>
                }
              </div>

              {/* ── Правая панель (текущая / proposed) ── */}
              <div style={{ flex: 1, background: rightBg, padding: '4px 10px', display: 'flex', alignItems: 'center', overflow: 'hidden' }}>
                <span style={{
                  color: overridden ? '#8a6d00' : rightClr,
                  whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                }}>
                  {effectiveRight}
                </span>
                {overridden && (
                  <Tag color="gold" style={{ marginLeft: 6, fontSize: 10, lineHeight: '14px', flexShrink: 0 }}>предложено</Tag>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* ── Легенда ── */}
      <div style={{ marginTop: 8, display: 'flex', gap: 16, fontSize: 11, color: '#8c8c8c' }}>
        <span><span style={{ background: '#ffeef0', padding: '1px 6px', borderRadius: 2 }}>красный</span> — удалено / было</span>
        <span><span style={{ background: '#e6ffed', padding: '1px 6px', borderRadius: 2 }}>зелёный</span> — добавлено / стало</span>
        <span><span style={{ background: '#fff9c4', padding: '1px 6px', borderRadius: 2 }}>жёлтый</span> — предложенное изменение</span>
        <span>← принять левое &nbsp; → принять правое</span>
      </div>
    </div>
  );
}

const headerStyle = (bg: string): React.CSSProperties => ({
  flex: 1, padding: '6px 10px', background: bg,
  fontFamily: 'monospace', fontSize: 12, fontWeight: 600, color: '#434343',
  borderBottom: 'none',
});
