import { Spin, Progress } from 'antd';

interface Props {
  progress: number;
}

export const ScanProgress: React.FC<Props> = ({ progress }) => (
  <div style={{ padding: 16 }}>
    <Spin tip={`Сканирование сети... ${progress}%`} />
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