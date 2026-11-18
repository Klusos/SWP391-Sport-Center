import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export function publicCssIsolation() {
  return {
    postcssPlugin: 'sportcenter-public-css-isolation',
    Rule(rule) {
      const file = rule.source?.input?.file?.replaceAll('\\', '/') ?? ''
      const isPublicStylesheet = file.endsWith('/src/public-site/styles.css')
      const isBootstrapStylesheet = file.includes('/node_modules/bootstrap/dist/css/bootstrap')
      const isBootstrapIcons = file.includes('/node_modules/bootstrap-icons/font/bootstrap-icons.css')
      if (!isPublicStylesheet && !isBootstrapStylesheet && !isBootstrapIcons) return

      let ancestor = rule.parent
      while (ancestor) {
        if (ancestor.type === 'atrule' && /keyframes/i.test(ancestor.name)) return
        ancestor = ancestor.parent
      }

      rule.selectors = rule.selectors.map((selector) => {
        const trimmed = selector.trim()
        // Some shared components declare every intended root explicitly in public-site/styles.css.
        const explicitScope = ['.public-site', '.lp', '.layout', '.member-course-detail']
        if (explicitScope.some((scope) => trimmed === scope || [' ', '>', '+', '~', '.', ':', '['].some((prefix) => trimmed.startsWith(`${scope}${prefix}`)))) return trimmed
        if (trimmed === ':root' || /^(html|body)(?=$|[.#:[\s>+~])/.test(trimmed)) {
          return `.public-site${trimmed.replace(/^:root|^(?:html|body)/, '')}`
        }
        if (trimmed === '*') return '.public-site, .public-site *'
        return `.public-site ${trimmed}`
      })
    },
  }
}

export default defineConfig({
  plugins: [react()],
  css: {
    postcss: { plugins: [publicCssIsolation()] },
  },
})
