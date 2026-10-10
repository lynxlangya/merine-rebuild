import type { ModelProvider, SaveModelProvider } from '@merine/api-contract';

type Template = {
  vendor: ModelProvider['vendor'];
  name: string;
  website: string;
  baseUrl: string;
  note: string;
  docs: string;
};
/** 官方通用 API 预设，2026-10-09 首次核对、2026-10-10 更新千问；只作填表起点，不代表已经连通。 */
export const providerTemplates: Template[] = [
  {
    vendor: 'DEEPSEEK',
    name: 'DeepSeek',
    website: 'https://platform.deepseek.com',
    baseUrl: 'https://api.deepseek.com',
    note: '使用通用 API 的 OpenAI 兼容地址。',
    docs: 'https://api-docs.deepseek.com/',
  },
  {
    vendor: 'QWEN',
    name: '千问 AI 平台',
    website: 'https://platform.qianwenai.com',
    baseUrl: 'https://maas.qianwenaiapi.com/compatible-mode/v1',
    note: '与官方文档一致的 OpenAI 兼容地址；按量付费 Key（sk-ws-）与 Token Plan 专用 Key（sk-sp-）不要混用。',
    docs: 'https://platform.qianwenai.com/docs/developer-guides/getting-started/first-api-call',
  },
  {
    vendor: 'ZHIPU',
    name: '智谱 GLM',
    website: 'https://open.bigmodel.cn',
    baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
    note: '预填通用 API 地址，编程套餐有单独的请求地址。',
    docs: 'https://docs.bigmodel.cn/api-reference/模型-api/对话补全',
  },
  {
    vendor: 'MOONSHOT',
    name: 'Kimi · 月之暗面',
    website: 'https://platform.kimi.com',
    baseUrl: 'https://api.moonshot.cn/v1',
    note: '使用开放平台 API Key；具体模型与思考参数后续单独配置。',
    docs: 'https://platform.kimi.com/docs/get-api-key',
  },
  {
    vendor: 'DOUBAO',
    name: '豆包 · 火山方舟',
    website: 'https://console.volcengine.com/ark',
    baseUrl: 'https://ark.cn-beijing.volces.com/api/v3',
    note: '预填北京通用 API 地址；调用时还需填写已开通的模型或推理接入点标识。',
    docs: 'https://docs.volcengine.com/docs/ark/compatible-with-openai-sdk?lang=zh',
  },
  {
    vendor: 'BAIDU',
    name: '文心 · 百度千帆',
    website: 'https://console.bce.baidu.com/qianfan',
    baseUrl: 'https://qianfan.baidubce.com/v2',
    note: '使用千帆通用 API Key，不是旧版 AK / SK 组合。',
    docs: 'https://cloud.baidu.com/doc/qianfan/s/Jmovazfdw',
  },
  {
    vendor: 'TENCENT',
    name: '腾讯混元',
    website: 'https://console.cloud.tencent.com/hunyuan',
    baseUrl: 'https://api.hunyuan.cloud.tencent.com/v1',
    note: '此预设为混元存量服务。官方正迁移至 TokenHub，新开通账号请按控制台修改地址。',
    docs: 'https://cloud.tencent.com/document/product/1729/111007',
  },
  {
    vendor: 'MINIMAX',
    name: 'MiniMax',
    website: 'https://platform.minimax.cn',
    baseUrl: 'https://api.minimax.cn/v1',
    note: '预填国内 OpenAI 兼容地址；部分模型的思考模式不可关闭。',
    docs: 'https://platform.minimax.cn/docs/api-reference/text-openai-api',
  },
  {
    vendor: 'CUSTOM',
    name: '自定义供应商',
    website: '',
    baseUrl: '',
    note: '填写供应商提供的 OpenAI Chat Completions 兼容基础地址。',
    docs: '',
  },
];
export function templateFor(vendor: ModelProvider['vendor']) {
  return providerTemplates.find((t) => t.vendor === vendor)!;
}
export function initialProvider(vendor: ModelProvider['vendor']): SaveModelProvider {
  const t = templateFor(vendor);
  return {
    vendor,
    name: t.vendor === 'CUSTOM' ? '' : t.name,
    remark: '',
    website: t.website,
    baseUrl: t.baseUrl,
    apiKey: '',
    status: 'ENABLED',
    models: [],
  };
}
