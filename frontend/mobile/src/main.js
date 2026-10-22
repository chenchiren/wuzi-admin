import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './styles.css'
import { startAutoSync } from './offline'

createApp(App).use(router).mount('#app')
startAutoSync()
