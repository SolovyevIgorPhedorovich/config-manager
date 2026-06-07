import { Progress, Typography } from 'antd';

interface Props {
  progress: number;
  scanned?: number;
  total?: number;
}

export const ScanProgress: React.FC<Props> = ({ progress, scanned, total }) => {
  const hasCounts = typeof total === 'number' && total > 0;
  const label = hasCounts
    ? `Сканирование сети… ${scanned ?? 0}/${total} адресов (${progress}%)`
    : `Сканирование сети… ${progress}%`;
  return (
    <div style={{ padding: 16 }}>
      <Typography.Text type="secondary">{label}</Typography.Text>
      <Progress
        percent={progress}
        status="active"
        strokeColor={{
          '0%': '#108ee9',
          '100%': '#87d068',
        }}
      />
    </div>
  );
};
