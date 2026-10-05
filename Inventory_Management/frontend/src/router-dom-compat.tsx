import { Children, isValidElement, useEffect, useMemo, createContext, useContext, type AnchorHTMLAttributes, type ReactNode } from 'react'
import {
  Link as WouterLink,
  Router as WouterRouter,
  useLocation as useWouterLocation,
} from 'wouter'
import { useHashLocation } from 'wouter/use-hash-location'

type NavigateOptions = { replace?: boolean; state?: unknown }

type LocationLike = {
  pathname: string
  search: string
  hash: string
  state: unknown
}

type MatchParams = Record<string, string>

type RouteProps = {
  path?: string
  element?: ReactNode
  children?: ReactNode
}

type NavLinkClassArg = { isActive: boolean }

type NavLinkProps = {
  to: string
  className?: string | ((arg: NavLinkClassArg) => string)
  children?: ReactNode
} & Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'className' | 'href'>

type LinkProps = {
  to: string
  className?: string
  children?: ReactNode
} & Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'className' | 'href'>

let transientNavState: unknown = null

const OutletContext = createContext<ReactNode>(null)
const ParamsContext = createContext<MatchParams>({})

export function HashRouter({ children }: { children: ReactNode }) {
  return <WouterRouter hook={useHashLocation}>{children}</WouterRouter>
}

export function Link({ to, className, children, ...rest }: LinkProps) {
  return (
    <WouterLink href={to} className={className} {...rest}>
      {children}
    </WouterLink>
  )
}

export function NavLink({ to, className, children, ...rest }: NavLinkProps) {
  const location = useLocation()
  const isActive = location.pathname === to || (to !== '/' && location.pathname.startsWith(`${to}/`))
  const computedClassName = typeof className === 'function' ? className({ isActive }) : className

  return (
    <Link to={to} className={computedClassName} {...rest}>
      {children}
    </Link>
  )
}

export function useNavigate() {
  const [, setLocation] = useWouterLocation()
  return (to: string, options?: NavigateOptions) => {
    transientNavState = options?.state ?? null
    setLocation(to, { replace: options?.replace })
  }
}

export function useLocation(): LocationLike {
  const [pathname] = useWouterLocation()
  return {
    pathname,
    search: '',
    hash: `#${pathname}`,
    state: transientNavState,
  }
}

export function useParams<T extends MatchParams = MatchParams>(): T {
  return useContext(ParamsContext) as T
}

export function Navigate({ to, replace, state }: { to: string; replace?: boolean; state?: unknown }) {
  const navigate = useNavigate()
  useEffect(() => {
    navigate(to, { replace, state })
  }, [navigate, replace, state, to])
  return null
}

export function Outlet() {
  return <>{useContext(OutletContext)}</>
}

export function Route(_props: RouteProps) {
  return null
}

type Branch = {
  path: string
  wrappers: ReactNode[]
}

function normalizePath(pathname: string): string {
  if (!pathname) return '/'
  const base = pathname.split('?')[0].split('#')[0]
  if (base.length > 1 && base.endsWith('/')) return base.slice(0, -1)
  return base
}

function matchPath(pattern: string, pathname: string): MatchParams | null {
  const cleanPattern = normalizePath(pattern)
  const cleanPath = normalizePath(pathname)

  if (cleanPattern === '*') return {}

  const patternParts = cleanPattern.split('/').filter(Boolean)
  const pathParts = cleanPath.split('/').filter(Boolean)

  if (patternParts.length !== pathParts.length) return null

  const params: MatchParams = {}
  for (let i = 0; i < patternParts.length; i += 1) {
    const p = patternParts[i]
    const v = pathParts[i]
    if (p.startsWith(':')) {
      params[p.slice(1)] = decodeURIComponent(v)
      continue
    }
    if (p !== v) return null
  }

  return params
}

function flattenRoutes(nodes: ReactNode, wrappers: ReactNode[] = []): Branch[] {
  const branches: Branch[] = []

  Children.forEach(nodes, (child) => {
    if (!isValidElement(child)) return

    const props = child.props as RouteProps
    const nextWrappers = props.element ? [...wrappers, props.element] : wrappers
    const childRoutes = props.children

    if (childRoutes) {
      branches.push(...flattenRoutes(childRoutes, nextWrappers))
      return
    }

    if (!props.path) return
    branches.push({ path: props.path, wrappers: nextWrappers })
  })

  return branches
}

function nestElements(elements: ReactNode[]): ReactNode {
  if (elements.length === 0) return null
  let rendered = elements[elements.length - 1]
  for (let i = elements.length - 2; i >= 0; i -= 1) {
    rendered = <OutletContext.Provider value={rendered}>{elements[i]}</OutletContext.Provider>
  }
  return rendered
}

export function Routes({ children }: { children: ReactNode }) {
  const location = useLocation()
  const livePath = normalizePath(location.pathname)

  const branches = useMemo(() => flattenRoutes(children), [children])

  for (const branch of branches) {
    const params = matchPath(branch.path, livePath)
    if (params) {
      return <ParamsContext.Provider value={params}>{nestElements(branch.wrappers)}</ParamsContext.Provider>
    }
  }

  return null
}
