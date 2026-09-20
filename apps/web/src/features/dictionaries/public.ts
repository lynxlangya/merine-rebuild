/**
 * 字典 feature 的显式出口。
 *
 * 使用侧只需要：一次会话级的批量查询（app 外壳调用）、按编码取字典、
 * 标签/选项转换与两个展示组件；维护页的 API 与查询留在 feature 内部。
 */
export { dictionaryKeys, useDictionariesQuery, useDictionary } from './queries';
export {
  DICTIONARY_CODES,
  dictLabel,
  dictTone,
  isDictionaryUsable,
  orderedItems,
  toDictOptions,
  type DictTone,
} from './model';
export { DictSelect } from './components/DictSelect';
export { DictTag } from './components/DictTag';
