<script>
import { computed, defineComponent, h } from 'vue'
import { getIcon } from './registry'
import { resolveNavIcon } from './icon.config'

const fallback = {
  viewBox: '0 0 24 24',
  parts: [{ tag: 'circle', attrs: { cx: '12', cy: '12', r: '8' } }]
}

export default defineComponent({
  name: 'AppIcon',
  props: {
    /** 图标名 */
    name: { type: String, default: '' },
    /** 导航业务键（group.key 或 item.path），按 navMap 解析 */
    navKey: { type: String, default: '' },
    size: { type: [Number, String], default: 18 },
    strokeWidth: { type: [Number, String], default: 1.8 }
  },
  setup(props) {
    const def = computed(() => {
      const key = props.navKey ? resolveNavIcon(props.navKey) : props.name
      return getIcon(key) || fallback
    })

    return () =>
      h(
        'svg',
        {
          class: 'app-icon',
          width: props.size,
          height: props.size,
          viewBox: def.value.viewBox,
          fill: 'none',
          stroke: 'currentColor',
          'stroke-width': props.strokeWidth,
          'stroke-linecap': 'round',
          'stroke-linejoin': 'round',
          'aria-hidden': 'true'
        },
        def.value.parts.map((p) => h(p.tag, p.attrs || {}))
      )
  }
})
</script>

<style scoped>
.app-icon {
  display: inline-block;
  flex-shrink: 0;
  vertical-align: middle;
}
</style>
