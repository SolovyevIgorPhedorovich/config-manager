import React, { useState } from 'react';
import { Card, Form, Input, Switch, Button, message, Tabs, InputNumber, Modal, Select, Row, Col } from 'antd';

const { TabPane } = Tabs;

export default function AdminPage() {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [changePasswordModalOpen, setChangePasswordModalOpen] = useState(false);
  const adEnabled = Form.useWatch('adEnabled', form)

  React.useEffect(() => {
    form.setFieldsValue({
      backupEnabled: true,
      configCollectionIntervalHours: 24,
      backupTaskStartTime: '02:00',
      configVersionRetentionDepth: 30,
      obsoleteVersionPolicy: 'keep-last-successful',
      loggingLevel: 'INFO',
      auditRetentionDays: 180,
      logSuccessfulLogins: true,
      logFailedLogins: true,
      sessionLifetimeMinutes: 60,
      maxFailedLoginAttempts: 5,
      workerThreads: 4,
      redisHost: 'localhost',
      redisPort: 6379,
      retryIntervalSeconds: 60,
      maxJobExecutionMinutes: 30,
      sshTimeoutSeconds: 30,
      winrmTimeoutSeconds: 45,
      reconnectAttempts: 3,
      snmpCommunity: 'public',
      snmpVersion: '2c',
      snmpTimeoutSeconds: 10,
      adEnabled: false,
      adUrl: 'ldap://dc.company.local:389',
      adBaseDn: 'DC=company,DC=local',
      adUserSearchFilter: '(sAMAccountName={0})'
    });
  }, [form]);

  const handleSubmit = async (values: any) => {
    try {
      setLoading(true);
      await fetch('/api/settings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(values)
      });
      console.log('Сохранено:', values);
      message.success('Настройки сохранены');
    } catch (err) {
      message.error('Ошибка при сохранении');
    } finally {
      setLoading(false);
    }
  };

  const handleChangePassword = async (values: any) => {
    try {
      await fetch('/api/auth/change-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(values)
      });
      message.success('Пароль изменён');
      setChangePasswordModalOpen(false);
    } catch (err) {
      message.error('Ошибка изменения пароля');
    }
  };

   return (
    <>
      <Tabs defaultActiveKey="1">
        {/* Общие настройки */}
        <TabPane tab="⚙️ Общие настройки" key="1">
          <Form form={form} layout="vertical" onFinish={handleSubmit}>
            <Row gutter={[16, 16]}>
              <Col xs={24} lg={12}>
                <Card title="Настройки резервного копирования конфигураций">
                  <Form.Item name="backupEnabled" label="Автоматическое резервное копирование" valuePropName="checked">
                    <Switch checkedChildren="Вкл" unCheckedChildren="Выкл" />
                  </Form.Item>
                  <Form.Item name="configCollectionIntervalHours" label="Периодичность автоматического сбора конфигураций (часов)" rules={[{ required: true, type: 'number', min: 1 }]}>
                    <InputNumber min={1} style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item name="backupTaskStartTime" label="Время запуска задач" rules={[{ required: true }]}>
                    <Input placeholder="02:00" />
                  </Form.Item>
                  <Form.Item name="configVersionRetentionDepth" label="Глубина хранения версий" rules={[{ required: true, type: 'number', min: 1 }]}>
                    <InputNumber min={1} style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item name="obsoleteVersionPolicy" label="Политика удаления устаревших версий" rules={[{ required: true }]}>
                    <Select
                      options={[
                        { value: 'keep-last-successful', label: 'Хранить последние успешные версии' },
                        { value: 'delete-after-retention', label: 'Удалять после истечения срока хранения' },
                        { value: 'archive-before-delete', label: 'Архивировать перед удалением' },
                      ]}
                    />
                  </Form.Item>
                </Card>
              </Col>

              <Col xs={24} lg={12}>
                <Card title="Настройки аудита и журналирования">
                  <Form.Item name="loggingLevel" label="Уровень логирования" rules={[{ required: true }]}>
                    <Select options={[{ value: 'DEBUG' }, { value: 'INFO' }, { value: 'WARN' }, { value: 'ERROR' }]} />
                  </Form.Item>
                  <Form.Item name="auditRetentionDays" label="Срок хранения аудита (дней)" rules={[{ required: true, type: 'number', min: 1 }]}>
                    <InputNumber min={1} style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item name="logSuccessfulLogins" label="Запись успешных попыток входа" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  <Form.Item name="logFailedLogins" label="Запись неуспешных попыток входа" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Card>
              </Col>

              <Col xs={24} lg={12}>
                <Card title="Настройки безопасности">
                  <Form.Item name="sessionLifetimeMinutes" label="Время жизни пользовательской сессии (минут)" rules={[{ required: true, type: 'number', min: 5 }]}>
                    <InputNumber min={5} style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item name="maxFailedLoginAttempts" label="Количество неудачных попыток входа" rules={[{ required: true, type: 'number', min: 1 }]}>
                    <InputNumber min={1} style={{ width: '100%' }} />
                  </Form.Item>
                </Card>
              </Col>

              <Col xs={24} lg={12}>
                <Card title="Настройки очередей и фоновых задач">
                  <Form.Item name="workerThreads" label="Количество потоков обработки" rules={[{ required: true, type: 'number', min: 1 }]}>
                    <InputNumber min={1} style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item name="redisHost" label="Redis host" rules={[{ required: true }]}>
                    <Input placeholder="localhost" />
                  </Form.Item>
                  <Form.Item name="redisPort" label="Redis port" rules={[{ required: true, type: 'number', min: 1, max: 65535 }]}>
                    <InputNumber min={1} max={65535} style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item name="retryIntervalSeconds" label="Интервалы повторных попыток (секунд)" rules={[{ required: true, type: 'number', min: 1 }]}>
                    <InputNumber min={1} style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item name="maxJobExecutionMinutes" label="Максимальное время выполнения задания (минут)" rules={[{ required: true, type: 'number', min: 1 }]}>
                    <InputNumber min={1} style={{ width: '100%' }} />
                  </Form.Item>
                </Card>
              </Col>

              <Col xs={24}>
                <Card title="Настройки устройств по умолчанию">
                  <Row gutter={16}>
                    <Col xs={24} md={8}>
                      <Form.Item name="sshTimeoutSeconds" label="Таймаут подключения SSH (секунд)" rules={[{ required: true, type: 'number', min: 1 }]}>
                        <InputNumber min={1} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="winrmTimeoutSeconds" label="Таймаут WinRM (секунд)" rules={[{ required: true, type: 'number', min: 1 }]}>
                        <InputNumber min={1} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="reconnectAttempts" label="Количество повторных подключений" rules={[{ required: true, type: 'number', min: 0 }]}>
                        <InputNumber min={0} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="snmpCommunity" label="SNMP community" rules={[{ required: true }]}>
                        <Input placeholder="public" />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="snmpVersion" label="SNMP version" rules={[{ required: true }]}>
                        <Select options={[{ value: '1', label: 'v1' }, { value: '2c', label: 'v2c' }, { value: '3', label: 'v3' }]} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="snmpTimeoutSeconds" label="SNMP timeout (секунд)" rules={[{ required: true, type: 'number', min: 1 }]}>
                        <InputNumber min={1} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                  </Row>
                </Card>
              </Col>
            </Row>

            <Button type="primary" htmlType="submit" loading={loading} style={{ marginTop: 16 }}>
              Сохранить общие настройки
            </Button>
          </Form>
        </TabPane>

        {/* Настройки AD */}
        <TabPane tab="👥 Active Directory" key="2">
          <Card title="Настройка интеграции с Active Directory">
            <Form form={form} layout="vertical" onFinish={handleSubmit}>
              <Form.Item
                name="adEnabled"
                label="Включить AD"
                valuePropName="checked"
              >
                <Switch />
              </Form.Item>

              <Form.Item
                name="adUrl"
                label="URL LDAP (AD)"
                rules={[{ required: true }]}
              > <Input
                placeholder="ldap://dc.company.local:389"
                disabled={!adEnabled}
              />
            </Form.Item>

              <Form.Item name="adBaseDn" label="Базовый DN">
            <Input
              placeholder="DC=company,DC=local"
              disabled={!adEnabled}
            />
          </Form.Item>


                <Form.Item name="adUserSearchFilter" label="Фильтр поиска пользователя">
              <Input
                placeholder="(sAMAccountName={0})"
                disabled={!adEnabled}
              />
            </Form.Item>

              <Button type="primary" htmlType="submit" loading={loading}>
                Сохранить настройки AD
              </Button>
            </Form>
          </Card>
        </TabPane>

        {/* Смена пароля */}
        <TabPane tab="🔑 Смена пароля admin" key="3">
          <Card title="Изменение пароля пользователя admin">
            <Button type="primary" onClick={() => setChangePasswordModalOpen(true)}>
              Изменить пароль
            </Button>
          </Card>
        </TabPane>

        {/* Отчёты */}
        <TabPane tab="📊 Отчёты" key="4">
          <Card>Здесь будут отчёты об изменениях и статистике.</Card>
        </TabPane>
      </Tabs>

      {/* Модальное окно смены пароля */}
      <Modal
        title="Изменить пароль администратора"
        open={changePasswordModalOpen}
        onCancel={() => setChangePasswordModalOpen(false)}
        footer={null}
      >
        <Form onFinish={handleChangePassword} layout="vertical">
          <Form.Item
            name="currentPassword"
            label="Текущий пароль"
            rules={[{ required: true }]}
          >
            <Input.Password />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="Новый пароль"
            rules={[
              { required: true },
              { min: 8, message: 'Минимум 8 символов' }
            ]}
          >
            <Input.Password />
          </Form.Item>
          <Form.Item
            name="confirmNewPassword"
            label="Повторите пароль"
            dependencies={['newPassword']}
            rules={[
              { required: true },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('Пароли не совпадают'));
                }
              })
            ]}
          >
            <Input.Password />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>Сохранить</Button>
        </Form>
      </Modal>
    </>
  );
}