import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';

gsap.registerPlugin(ScrollTrigger);

const DEFAULT_REVEAL_SELECTOR = [
  '.auth-window',
  '.sidebar',
  '.sidebar-rail',
  '.chat-header',
  '.chat-welcome',
  '.composer',
  '.admin-header',
  '.kc-title-row',
  '.kc-metric-card',
  '.kc-table-card'
].join(',');

export function createQuietReveal(rootElement, options = {}) {
  if (!rootElement) {
    return () => {};
  }

  const { scroller = null, selector = DEFAULT_REVEAL_SELECTOR } = options;
  const context = gsap.context(() => {
    const elements = gsap.utils.toArray(selector).filter((element) => element.offsetParent !== null);

    elements.forEach((element, index) => {
      gsap.fromTo(
        element,
        {
          autoAlpha: 0,
          y: 12
        },
        {
          autoAlpha: 1,
          y: 0,
          duration: 0.52,
          delay: Math.min(index * 0.035, 0.18),
          ease: 'power3.out',
          clearProps: 'transform,opacity,visibility',
          scrollTrigger: {
            trigger: element,
            scroller: scroller || undefined,
            start: 'top 96%',
            once: true
          }
        }
      );
    });
  }, rootElement);

  return () => {
    context.revert();
    ScrollTrigger.refresh();
  };
}
