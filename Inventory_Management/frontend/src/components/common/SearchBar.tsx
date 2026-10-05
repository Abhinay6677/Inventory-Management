import { FiSearch } from 'react-icons/fi'

interface Props {
  value: string
  onChange: (value: string) => void
  placeholder?: string
}

export function SearchBar({ value, onChange, placeholder = 'Search...' }: Props) {
  return (
    <div className="input-group search-bar">
      <span className="input-group-text bg-white"><FiSearch /></span>
      <input
        className="form-control"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
      />
    </div>
  )
}
