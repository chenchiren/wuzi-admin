import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import naive from 'naive-ui'
import App from './App.vue'
import router from './router'
import './styles/index.css'
import { setupIcons } from './icons'
import { loadDicts } from './utils/dict'
import vPerm from './directives/perm'
import { loadPermsFromStorage } from './utils/perm'

setupIcons()

// 分页默认英文 Total → 中文「总数」
const locale = {
  ...zhCn,
  el: {
    ...zhCn.el,
    pagination: {
      ...zhCn.el.pagination,
      total: '总数 {total}'
    }
  }
}

const app = createApp(App)
app.use(ElementPlus, { locale })
app.use(naive)
app.use(router)
app.directive('perm', vPerm)
loadPermsFromStorage()
app.mount('#app')

// 登录后预加载界面字典（状态/类型中文）
if (localStorage.getItem('token')) {
  loadDicts(true)
}
